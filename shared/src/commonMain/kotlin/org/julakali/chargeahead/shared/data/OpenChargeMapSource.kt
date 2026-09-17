package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeSiteSource
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.SearchArea
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

/** OpenChargeMap as a charging-site source. */
class OpenChargeMapSource(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val maxResults: Int = DEFAULT_MAX_RESULTS,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : ChargeSiteSource {

    override val id: String = SOURCE_ID

    /**
     * Queried radially, not rectangularly: with `boundingbox`, OCM responds
     * unsorted and truncates at `maxresults`.
     */
    override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> = when (area) {
        // OCM has no concept of a polyline.
        is PolylineArea -> area.radialCover()
            .flatMap { queryCircle(it, networks) }
            .distinctBy { it.id }

        else -> queryCircle(area, networks)
    }

    private suspend fun queryCircle(area: SearchArea, networks: List<Network> = emptyList()): List<ChargeSite> {
        val response: List<OcmPoi> = httpClient.get(baseUrl) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            // TODO keep only the header once verified against the live API
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
     * OCM's placeholder operators like `(Unknown Operator)` are detected by
     * the leading parenthesis.
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
            // Missing means unknown, not "one".
            count = connection.quantity?.takeIf { it > 0 },
        )
    }

    /**
     * OCM connector-type numbers (`/v3/referencedata`) mapped to [ConnectorType].
     * Anything unrecognized becomes [ConnectorType.UNKNOWN].
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

        const val DEFAULT_MAX_RESULTS = 500

        const val USER_AGENT = "ChargeAhead"
    }
}
