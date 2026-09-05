package de.autoapp.shared.data

import de.autoapp.shared.domain.Geocoder
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Place
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Destination search via Nominatim.
 *
 * Chosen because it's the easiest to swap for a self-hosted instance: a
 * self-run Nominatim exposes the same API as the public one, so switching is
 * a one-line change to [baseUrl]. Photon would be better for autocomplete,
 * but self-hosting it would require a Nominatim import first anyway.
 *
 * **Mind the public instance's usage policy.** It requires a recognizable
 * User-Agent and at most one request per second, and it rules out bulk use.
 * That's respected here because the search only runs on destination entry —
 * once per trip, not once per location update. Anyone changing that needs to
 * move to a self-hosted instance first.
 *
 * Data comes from OpenStreetMap (ODbL) and must be attributed accordingly.
 */
class NominatimGeocoder(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : Geocoder {

    override suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> {
        if (query.isBlank()) return emptyList()

        val response: List<NominatimPlace> = httpClient.get("$baseUrl/search") {
            header(HttpHeaders.UserAgent, OpenChargeMapSource.USER_AGENT)
            parameter("q", query)
            parameter("format", "jsonv2")
            parameter("limit", limit)
            // The broken-down address isn't needed; display_name is enough.
            parameter("addressdetails", 0)
            near?.let {
                // viewbox biases toward nearby results without forcing them:
                // without bounded=1, distant destinations stay findable.
                val box = VIEWBOX_DEGREES
                parameter(
                    "viewbox",
                    "${it.lon - box},${it.lat - box},${it.lon + box},${it.lat + box}",
                )
            }
        }.body()

        return response.mapNotNull(NominatimPlace::toPlace)
            // Same place only once: OSM often carries a place twice, as a
            // boundary relation and as a place node, and Nominatim returns
            // both. Searching "Münster" from Kiel returned "Munster,
            // Heidekreis, Niedersachsen, 29633, Deutschland" twice in the
            // same response. Nobody can tell apart two rows with identical
            // labels — the second one is just confusion.
            .distinctBy { it.description }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://nominatim.openstreetmap.org"

        /** About 110 km wide — the surrounding area a destination is likely to be in. */
        private const val VIEWBOX_DEGREES = 1.0
    }
}

@Serializable
internal data class NominatimPlace(
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    /** Nominatim returns coordinates as strings, not numbers. */
    val lat: String? = null,
    val lon: String? = null,
) {
    fun toPlace(): Place? {
        val latitude = lat?.toDoubleOrNull() ?: return null
        val longitude = lon?.toDoubleOrNull() ?: return null
        val full = displayName?.takeIf { it.isNotBlank() }

        // Without a short name, take the first part of the description —
        // "München Hauptbahnhof" instead of the full chain down to "Deutschland".
        val short = name?.takeIf { it.isNotBlank() }
            ?: full?.substringBefore(",")?.trim()
            ?: return null

        return Place(name = short, description = full ?: short, position = LatLon(latitude, longitude))
    }
}
