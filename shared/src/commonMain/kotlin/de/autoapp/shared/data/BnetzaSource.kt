package de.autoapp.shared.data

import de.autoapp.shared.domain.Address
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ChargeSiteSource
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.SearchArea
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

/**
 * The Bundesnetzagentur's charging register — officially reported data for
 * Germany (ARCHITECTURE.md section 6).
 *
 * No key needed, but it's an ArcGIS FeatureServer rather than a dedicated
 * API. Verified against the live service (2026-09-04).
 *
 * **Not the endpoint documented at `ladestationen.api.bund.dev`.** That one
 * (`services6.arcgis.com/…/Ladesaeulenregister/FeatureServer/7`) now responds
 * with `Token Required`; its documentation is outdated. This uses the service
 * behind the Bundesnetzagentur's own charging-station map.
 *
 * Three quirks that shape this adapter:
 *
 * 1. **Rectangle instead of radius.** ArcGIS has no distance-based sorting.
 *    So the queried area must stay small enough for the response to be
 *    complete — measured, the full 175 km corridor holds 33,508 charging
 *    facilities, a 50 km radius holds 3,312. This is not bounded here but
 *    by the caller (`TiledSiteRepository.maxRadiusKm`), so the cache only
 *    marks as covered the area that was actually fetched.
 * 2. **Errors arrive with HTTP 200.** ArcGIS puts them in an `error` object in
 *    the body. Without the check below, a server error would look like "no
 *    charge site here."
 * 3. **A row is a charging facility, not a site.** A service area has several.
 *    Merging them is handled by the dedup stage.
 */
class BnetzaSource(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) : ChargeSiteSource {

    override val id: String = SOURCE_ID

    override suspend fun query(area: SearchArea): List<ChargeSite> {
        val sites = mutableListOf<ChargeSite>()
        var offset = 0

        repeat(maxPages) {
            val response = fetchPage(area, offset)
            response.error?.let { error ->
                throw IllegalStateException("Charging station registry: ${error.message} (${error.code})")
            }
            sites += response.features.mapNotNull { toChargeSite(it.attributes) }

            if (!response.exceededTransferLimit || response.features.isEmpty()) return sites
            offset += response.features.size
        }
        return sites
    }

    private suspend fun fetchPage(area: SearchArea, offset: Int): ArcGisResponse {
        return httpClient.get(baseUrl) {
            header(HttpHeaders.UserAgent, OpenChargeMapSource.USER_AGENT)
            parameter("f", "json")
            applyGeometry(area)
            parameter("inSR", "4326")
            parameter("spatialRel", "esriSpatialRelIntersects")
            // A charging facility under maintenance is not a viable charging stop.
            // The service only knows these two values — verified nationwide.
            parameter("where", "Status='In Betrieb'")
            parameter("outFields", REQUESTED_FIELDS)
            // Coordinates already come back as fields; the geometry would just
            // be the same information a second time.
            parameter("returnGeometry", "false")
            parameter("resultRecordCount", pageSize)
            parameter("resultOffset", offset)
        }.body()
    }

    /**
     * The search area as ArcGIS geometry.
     *
     * For a route, the **polyline with buffer** is passed, not its bounding
     * rectangle: the service then clips server-side, which is exactly where
     * the volume reduction comes from. For the same Nuremberg-Munich trip,
     * that's 769 charging facilities instead of 33,508 — a rectangle around
     * the route would fall in between and buy nothing.
     */
    private fun HttpRequestBuilder.applyGeometry(area: SearchArea) {
        when (area) {
            is PolylineArea -> {
                val path = area.points.joinToString(",") { "[${it.lon},${it.lat}]" }
                parameter("geometryType", "esriGeometryPolyline")
                parameter("geometry", """{"paths":[[$path]],"spatialReference":{"wkid":4326}}""")
                // ArcGIS does its own buffering; meters, because the service
                // otherwise works in degrees, and a degree value would mean
                // something different depending on latitude.
                parameter("distance", area.bufferKm * 1000.0)
                parameter("units", "esriSRUnit_Meter")
            }

            else -> {
                val box = area.boundingBox
                parameter("geometryType", "esriGeometryEnvelope")
                parameter(
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
     * Each entry within a field is one individual charging point: a field
     * holding `"AC Typ 2 Steckdose; AC Typ 2 Steckdose"` with `"22; 22"` means
     * two units at 22 kW each. Matching type/power pairs are therefore
     * counted — unlike OpenChargeMap, the unit count is actually known here.
     */
    private fun BnetzaAttributes.toConnectors(): List<Connector> {
        val counted = LinkedHashMap<Pair<ConnectorType, Double>, Int>()

        connectorSlots.forEach { (types, powers) ->
            val typeList = types.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val powerList = powers.orEmpty().split(";").map { it.trim() }

            typeList.forEachIndexed { index, typeName ->
                // If the source lists only one power value for several connectors,
                // it applies to all of them — observed as "175; 175", but not guaranteed.
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
     *
     * Exhaustive: nationwide, the service knows exactly these six values,
     * confirmed via `returnDistinctValues` rather than guessed.
     *
     * `AC CEE 3-polig` stays [ConnectorType.UNKNOWN] — there's no enum value
     * for it here, just as with the CEE variants from OpenChargeMap.
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

        /**
         * The service behind the Bundesnetzagentur's charging-station map.
         *
         * The layer name carries the data snapshot date (`Ladesäulen_072026`),
         * but the layer number stays stable. That's why it's addressed by the
         * 0 and never by name.
         */
        const val DEFAULT_BASE_URL =
            "https://services2.arcgis.com/jUpNdisbWqRpMo35/arcgis/rest/services/" +
                "Ladesaeulen_in_Deutschland/FeatureServer/0/query"

        /** The service's per-request cap; it never returns more than this anyway. */
        const val DEFAULT_PAGE_SIZE = 2000

        /**
         * Safety brake against endless paging. At 2000 per page that's 20,000
         * charging facilities — well more than a reasonably bounded area
         * contains, and far below the 33,508 of the full corridor.
         */
        const val DEFAULT_MAX_PAGES = 10

        private const val REQUESTED_FIELDS =
            "Ladeeinrichtungs_ID,Betreiber,Standortbezeichnung,Breitengrad,Längengrad," +
                "Straße,Hausnummer,Postleitzahl,Ort," +
                "Steckertypen1,Nennleistung_Stecker1,Steckertypen2,Nennleistung_Stecker2," +
                "Steckertypen3,Nennleistung_Stecker3,Steckertypen4,Nennleistung_Stecker4," +
                "Steckertypen5,Nennleistung_Stecker5,Steckertypen6,Nennleistung_Stecker6"
    }
}
