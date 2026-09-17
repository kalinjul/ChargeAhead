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
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.ParametersBuilder
import io.ktor.http.parameters

/**
 * The Bundesnetzagentur's charging register — officially reported data for
 * Germany, served by the ArcGIS FeatureServer behind the Bundesnetzagentur's
 * own charging-station map.
 *
 * - ArcGIS has no distance-based sorting, so the caller must keep the area small.
 * - Errors arrive with HTTP 200, in an `error` object in the body.
 * - A row is a charging facility, not a site.
 */
class BnetzaSource(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) : ChargeSiteSource {

    override val id: String = SOURCE_ID

    override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> {
        val sites = mutableListOf<ChargeSite>()
        var offset = 0

        repeat(maxPages) {
            val response = fetchPage(area, offset, networks)
            response.error?.let { error ->
                throw IllegalStateException("Charging station registry: ${error.message} (${error.code})")
            }
            sites += response.features.mapNotNull { toChargeSite(it.attributes) }

            if (!response.exceededTransferLimit || response.features.isEmpty()) return sites
            offset += response.features.size
        }
        return sites
    }

    private fun whereClause(networks: List<Network>): String {
        val base = "Status='In Betrieb'"
        if (networks.isEmpty()) return base
        val likes = networks.flatMap { it.nameKeywords }
            .map { "UPPER(Betreiber) LIKE '%${it.uppercase().replace("'", "''")}%'" }
        return "$base AND (${likes.joinToString(" OR ")})"
    }

    private suspend fun fetchPage(area: SearchArea, offset: Int, networks: List<Network>): ArcGisResponse {
        // POST, not GET: a route's polyline outgrows the URL.
        val form = parameters {
            append("f", "json")
            applyGeometry(area)
            append("inSR", "4326")
            append("spatialRel", "esriSpatialRelIntersects")
            // With networks, a LIKE prefilter; resolve() refines on-device.
            append("where", whereClause(networks))
            append("outFields", REQUESTED_FIELDS)
            // Coordinates already come back as fields.
            append("returnGeometry", "false")
            append("resultRecordCount", pageSize.toString())
            append("resultOffset", offset.toString())
        }
        return httpClient.submitForm(url = baseUrl, formParameters = form) {
            header(HttpHeaders.UserAgent, OpenChargeMapSource.USER_AGENT)
        }.body()
    }

    /**
     * The search area as ArcGIS geometry. For a route, the polyline with
     * buffer, so the service clips server-side.
     */
    private fun ParametersBuilder.applyGeometry(area: SearchArea) {
        when (area) {
            is PolylineArea -> {
                val path = area.points.joinToString(",") { "[${it.lon},${it.lat}]" }
                append("geometryType", "esriGeometryPolyline")
                append("geometry", """{"paths":[[$path]],"spatialReference":{"wkid":4326}}""")
                append("distance", (area.bufferKm * 1000.0).toString())
                append("units", "esriSRUnit_Meter")
            }

            else -> {
                val box = area.boundingBox
                append("geometryType", "esriGeometryEnvelope")
                append(
                    "geometry",
                    """{"xmin":${box.west},"ymin":${box.south},"xmax":${box.east},"ymax":${box.north},""" +
                        """"spatialReference":{"wkid":4326}}""",
                )
            }
        }
    }

    /** Street and house number arrive as separate fields; display needs them combined. */
    private fun BnetzaAttributes.toAddress(): Address? {
        val streetLine = listOfNotNull(
            street?.trim()?.takeIf { it.isNotBlank() },
            houseNumber?.trim()?.takeIf { it.isNotBlank() },
        ).joinToString(" ").takeIf { it.isNotBlank() }

        return Address(
            street = streetLine,
            postalCode = postalCode?.trim()?.takeIf { it.isNotBlank() },
            town = town?.trim()?.takeIf { it.isNotBlank() },
        ).takeIf { !it.isEmpty }
    }

    private fun toChargeSite(attributes: BnetzaAttributes): ChargeSite? {
        val id = attributes.id ?: return null
        val latitude = attributes.latitude ?: return null
        val longitude = attributes.longitude ?: return null

        return ChargeSite(
            id = "$SOURCE_ID:$id",
            name = attributes.locationName?.takeIf { it.isNotBlank() }
                ?: attributes.town?.takeIf { it.isNotBlank() }
                ?: "$SOURCE_ID:$id",
            operator = attributes.operator?.trim()?.takeIf { it.isNotEmpty() },
            position = LatLon(latitude, longitude),
            connectors = attributes.toConnectors(),
            address = attributes.toAddress(),
            sources = setOf(SOURCE_ID),
        )
    }

    /**
     * The six connector slots, turned into connectors.
     *
     * Each entry within a field is one charging point, so matching
     * type/power pairs are counted.
     */
    private fun BnetzaAttributes.toConnectors(): List<Connector> {
        val counted = LinkedHashMap<Pair<ConnectorType, Double>, Int>()

        connectorSlots.forEach { (types, powers) ->
            val typeList = types.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val powerList = powers.orEmpty().split(";").map { it.trim() }

            typeList.forEachIndexed { index, typeName ->
                // A single power value applies to all connectors.
                val powerText = powerList.getOrNull(index) ?: powerList.firstOrNull()
                val powerKw = powerText?.replace(',', '.')?.toDoubleOrNull() ?: return@forEachIndexed
                if (powerKw <= 0.0) return@forEachIndexed

                val key = connectorTypeOf(typeName) to powerKw
                counted[key] = (counted[key] ?: 0) + 1
            }
        }

        return counted.map { (key, count) -> Connector(key.first, key.second, count) }
    }

    /**
     * The register's connector labels.
     */
    private fun connectorTypeOf(name: String): ConnectorType = when (name) {
        "AC Typ 2 Steckdose", "AC Typ 2 Fahrzeugkupplung" -> ConnectorType.TYPE2
        "DC Fahrzeugkupplung Typ Combo 2 (CCS)" -> ConnectorType.CCS2
        "DC CHAdeMO" -> ConnectorType.CHADEMO
        "AC Schuko" -> ConnectorType.SCHUKO
        else -> ConnectorType.UNKNOWN
    }

    companion object {
        const val SOURCE_ID = "bnetza"

        /** Addressed by layer number, which stays stable; the layer name carries the snapshot date. */
        const val DEFAULT_BASE_URL =
            "https://services2.arcgis.com/jUpNdisbWqRpMo35/arcgis/rest/services/" +
                "Ladesaeulen_in_Deutschland/FeatureServer/0/query"

        /** The service's per-request cap. */
        const val DEFAULT_PAGE_SIZE = 2000

        /** Safety brake against endless paging. */
        const val DEFAULT_MAX_PAGES = 10

        private const val REQUESTED_FIELDS =
            "Ladeeinrichtungs_ID,Betreiber,Standortbezeichnung,Breitengrad,Längengrad," +
                "Straße,Hausnummer,Postleitzahl,Ort," +
                "Steckertypen1,Nennleistung_Stecker1,Steckertypen2,Nennleistung_Stecker2," +
                "Steckertypen3,Nennleistung_Stecker3,Steckertypen4,Nennleistung_Stecker4," +
                "Steckertypen5,Nennleistung_Stecker5,Steckertypen6,Nennleistung_Stecker6"
    }
}
