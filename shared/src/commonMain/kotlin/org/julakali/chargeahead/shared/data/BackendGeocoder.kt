package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.Place
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.julakali.chargeahead.api.PlaceDto
import org.julakali.chargeahead.api.PlacesResponse

/** Destination search through the ChargeAhead backend. */
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
