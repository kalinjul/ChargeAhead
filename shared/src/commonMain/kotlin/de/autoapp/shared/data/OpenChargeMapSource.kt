package de.autoapp.shared.data

import de.autoapp.shared.domain.Address
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ChargeSiteSource
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.SearchArea
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

/**
 * OpenChargeMap as a charging-site source (M1).
 *
 * The first source, because it's the only one immediately queryable by
 * rectangle and with international coverage (ARCHITECTURE.md section 6).
 * Without a key, the API responds with HTTP 403 — that error is not caught
 * but propagated upward: without a key, this source is never used at all,
 * [DemoSiteSource] is used instead.
 *
 * Verified against the live API (2026-09-03, area along the A9 between
 * Nuremberg and Ingolstadt, 123 sites): `OperatorInfo` comes as an object,
 * `AddressInfo` carries title and coordinates, `Connections` carry
 * `ConnectionTypeID`, `PowerKW` and `Quantity`. The connector-type numbers in
 * [connectorTypeOf] match `/v3/referencedata`.
 *
 * Two quirks of the real data shape the code — see [operatorTitleOf] and
 * [OcmConnection.quantity].
 */
class OpenChargeMapSource(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val maxResults: Int = DEFAULT_MAX_RESULTS,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : ChargeSiteSource {

    override val id: String = SOURCE_ID

    /**
     * Queried radially, not rectangularly — and that's not a minor detail.
     *
     * With `boundingbox`, OCM responds **unsorted** and truncates at
     * `maxresults`. Measured against a corridor along the A9 (48.95/11.45,
     * 175 km): of 500 returned sites, the nearest was 70 km away, and the
     * first ten in the response were 202 to 233 km out — while a charging
     * site sitting 2 km away was simply missing.
     *
     * With `latitude`/`longitude`/`distance`, OCM sorts ascending by
     * distance. The same query then returned 1.9 km as the nearest hit.
     * So the cap truncates the farthest results instead of the nearest.
     */
    override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> = when (area) {
        // OCM has no concept of a polyline. So the corridor is broken into a
        // chain of circles and queried section by section — the bounding
        // rectangle of a route would be uselessly large.
        is PolylineArea -> area.radialCover()
            .flatMap { queryCircle(it, networks) }
            .distinctBy { it.id }

        else -> queryCircle(area, networks)
    }

    private suspend fun queryCircle(area: SearchArea, networks: List<Network> = emptyList()): List<ChargeSite> {
        val response: List<OcmPoi> = httpClient.get(baseUrl) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            // Deliberately duplicated: OCM accepts the key both as a header
            // and as a query parameter. Which of the two the API actually
            // honors today can't be verified here without a real key — and a
            // 403 from picking the wrong one would be an hours-long,
            // unexplainable bug. Once a real request has succeeded, only the
            // header should remain.
            header(API_KEY_HEADER, apiKey)
            parameter("key", apiKey)
            parameter("output", "json")
            parameter("latitude", area.origin.lat)
            parameter("longitude", area.origin.lon)
            parameter("distance", area.radiusKm)
            parameter("distanceunit", "KM")
            parameter("maxresults", maxResults)
            if (networks.isNotEmpty()) {
                val ids = networks.flatMap { it.operatorIds }.distinct().sorted().joinToString(",")
                parameter("operatorid", ids)
            }
        }.body()

        return response.mapNotNull(::toChargeSite)
    }

    private fun toChargeSite(poi: OcmPoi): ChargeSite? {
        val id = poi.id ?: return null
        val address = poi.addressInfo ?: return null
        val latitude = address.latitude ?: return null
        val longitude = address.longitude ?: return null

        // Without a name, the row in the car would be blank; the town is the
        // best fallback, and where that's missing too, only the id remains.
        val name = address.title?.takeIf { it.isNotBlank() }
            ?: address.town?.takeIf { it.isNotBlank() }
            ?: "$SOURCE_ID:$id"

        return ChargeSite(
            id = "$SOURCE_ID:$id",
            name = name,
            operator = operatorTitleOf(poi),
            operatorId = poi.operatorId,
            position = LatLon(latitude, longitude),
            connectors = poi.connections.orEmpty().mapNotNull(::toConnector),
            address = Address(
                street = address.addressLine1?.takeIf { it.isNotBlank() },
                postalCode = address.postcode?.takeIf { it.isNotBlank() },
                town = address.town?.takeIf { it.isNotBlank() },
            ).takeIf { !it.isEmpty },
            sources = setOf(SOURCE_ID),
        )
    }

    /**
     * Operator name — or `null` where OCM doesn't know one.
     *
     * OCM lists three placeholders as regular "operators":
     * `(Business Owner at Location)`, `(Private Residence/Individual)` and
     * `(Unknown Operator)`. In the sample along the A9, 38 of 123 sites
     * carried one of these — nearly a third. Unfiltered, the car would show
     * "Ladepark X · (Business Owner at Location)".
     *
     * Detected by the leading parenthesis: of 997 operators in the reference
     * data, exactly these three start with one. Real names containing
     * parentheses, like "EnBW (D)" or "Shell Recharge Solutions (DE)", never
     * have one at the front.
     */
    private fun operatorTitleOf(poi: OcmPoi): String? =
        poi.operatorInfo?.title
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("(") }

    private fun toConnector(connection: OcmConnection): Connector? {
        val powerKw = connection.powerKw ?: return null
        if (powerKw <= 0.0) return null
        return Connector(
            type = connectorTypeOf(connection.connectionTypeId),
            maxPowerKw = powerKw,
            // Don't default to 1: missing means unknown, not "one." In the
            // sample, the value was missing for 159 of 307 connections, and
            // 22 carried a 0.
            count = connection.quantity?.takeIf { it > 0 },
        )
    }

    /**
     * OCM connector-type numbers mapped to [ConnectorType].
     *
     * Matched against `/v3/referencedata` (2026-09-03, 43 types).
     *
     * Deliberately not mapped, even though they occur in the data:
     * - **1 (Type 1 / J1772)** and **32 (CCS Type 1)** — North American
     *   connectors, for which there's no enum value here.
     * - **8 / 30 (Tesla Roadster and Model S/X respectively)** — physically
     *   Type 2 in Europe, proprietary in North America. Either mapping would
     *   be wrong depending on continent, and a wrong connector type shown in
     *   the car is worse than an unknown one.
     * - **13/16/17/18/23 (CEE and Europlug variants)** — 16 connections in
     *   the sample area alone, but with no dedicated enum value.
     *
     * Anything unrecognized becomes [ConnectorType.UNKNOWN] and is thus
     * visible, rather than silently disappearing.
     */
    private fun connectorTypeOf(connectionTypeId: Int?): ConnectorType = when (connectionTypeId) {
        2, 1044 -> ConnectorType.CHADEMO // 1044 is ChaoJi / CHAdeMO 3.x
        25, 1036 -> ConnectorType.TYPE2 // socket or fixed/tethered cable respectively
        33 -> ConnectorType.CCS2
        27 -> ConnectorType.TESLA_NACS
        28 -> ConnectorType.SCHUKO
        else -> ConnectorType.UNKNOWN
    }

    companion object {
        const val SOURCE_ID = "ocm"

        const val API_KEY_HEADER = "X-API-Key"

        const val DEFAULT_BASE_URL = "https://api.openchargemap.io/v3/poi"

        /**
         * A 150 km-radius corridor over Germany holds far more than the 100
         * sites OCM returns when no limit is given. 500 also covers dense
         * metro areas without letting the response balloon to megabytes.
         *
         * The cap does bite in practice — in the corridor along the A9, the
         * 500 nearest were exhausted after just 55 km. But because the query
         * is radial, that trims the farthest results, not the nearest.
         */
        const val DEFAULT_MAX_RESULTS = 500

        /** OCM explicitly asks for a recognizable User-Agent per application. */
        const val USER_AGENT = "autoapp-ladesaeulen-assistent"
    }
}
