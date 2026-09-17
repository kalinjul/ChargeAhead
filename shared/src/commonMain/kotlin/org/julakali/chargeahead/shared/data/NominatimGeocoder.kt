package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.Place
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
 * **Mind the public instance's usage policy**: recognizable User-Agent, at
 * most one request per second, no bulk use.
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
            parameter("addressdetails", 1)
            near?.let {
                // Biases toward nearby results; without bounded=1 it doesn't restrict them.
                val box = VIEWBOX_DEGREES
                parameter(
                    "viewbox",
                    "${it.lon - box},${it.lat - box},${it.lon + box},${it.lat + box}",
                )
            }
        }.body()

        return response.mapNotNull(NominatimPlace::toPlace)
            // OSM often carries a place twice (boundary relation and place node).
            .distinctBy { it.description }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://nominatim.openstreetmap.org"

        /** About 110 km wide. */
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
    val address: NominatimAddress? = null,
) {
    fun toPlace(): Place? {
        val latitude = lat?.toDoubleOrNull() ?: return null
        val longitude = lon?.toDoubleOrNull() ?: return null
        val full = displayName?.takeIf { it.isNotBlank() }

        // Without a short name, take the first part of the description.
        val short = name?.takeIf { it.isNotBlank() }
            ?: full?.substringBefore(",")?.trim()
            ?: return null

        return Place(
            name = short,
            description = full ?: short,
            position = LatLon(latitude, longitude),
            address = address?.toAddress(),
        )
    }
}

@Serializable
internal data class NominatimAddress(
    val road: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
    val postcode: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null,
    val hamlet: String? = null,
) {
    fun toAddress(): Address? = Address(
        street = listOfNotNull(road, houseNumber).joinToString(" ").takeIf { it.isNotBlank() },
        postalCode = postcode,
        // Only one of these is ever set.
        town = city ?: town ?: village ?: municipality ?: hamlet,
    ).takeIf { !it.isEmpty }
}
