package de.autoapp.shared.data

import de.autoapp.shared.domain.Address
import de.autoapp.shared.domain.Geocoder
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Place
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.julakali.chargeahead.api.PlaceDto
import org.julakali.chargeahead.api.PlacesResponse

/**
 * Destination search through the ChargeAhead backend.
 *
 * The server answers from Photon, which [NominatimGeocoder] could not: a
 * geocoder resolves a finished address, while "Münch" is half a word. The
 * rate limit and the deduplication of doubled OSM entries sit there too.
 */
class BackendGeocoder(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String,
) : Geocoder {

    override suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> {
        if (query.isBlank()) return emptyList()

        val response: PlacesResponse = httpClient.get("${baseUrl.trimEnd('/')}$PATH") {
            bearerAuth(token)
            parameter("q", query)
            parameter("limit", limit)
            near?.let {
                parameter("lat", it.lat)
                parameter("lon", it.lon)
            }
        }.body()

        return response.places.map { it.toPlace() }
    }

    private companion object {
        const val PATH = "/v1/places"
    }
}

private fun PlaceDto.toPlace() = Place(
    name = name,
    description = description,
    position = LatLon(position.lat, position.lon),
    address = address?.let { Address(street = it.street, postalCode = it.postalCode, town = it.town) },
)
