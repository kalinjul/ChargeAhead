package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.RouteSite
import org.julakali.chargeahead.shared.domain.RouteSiteSource
import org.julakali.chargeahead.shared.domain.RouteWithSites
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.julakali.chargeahead.api.NetworkFilterDto
import org.julakali.chargeahead.api.RouteChargeSitesRequest
import org.julakali.chargeahead.api.RouteChargeSitesResponse

class BackendRouteSiteSource(
    private val httpClient: HttpClient,
) : RouteSiteSource {

    override suspend fun routeWithSites(
        from: LatLon,
        to: LatLon,
        bufferKm: Double,
        networkKeys: Set<String>,
        minPowerKw: Double,
    ): RouteWithSites? {
        val response = httpClient.post(PATH) {
            contentType(ContentType.Application.Json)
            setBody(
                RouteChargeSitesRequest(
                    from = from.toDto(),
                    to = to.toDto(),
                    bufferKm = bufferKm,
                    networks = networkKeys.map { NetworkFilterDto(key = it) },
                    minPowerKw = minPowerKw,
                ),
            )
        }

        // 204: no road connection.
        if (response.status == HttpStatusCode.NoContent) return null

        val found: RouteChargeSitesResponse = response.body()
        val route = found.route.toDomain() ?: return null
        return RouteWithSites(
            route = route,
            sites = found.sites.map {
                RouteSite(
                    site = it.site.toDomain(),
                    kmFromStart = it.kmFromStart,
                    distanceFromRouteKm = it.distanceFromRouteKm,
                )
            },
        )
    }

    private companion object {
        const val PATH = "v1/route/charge-sites"
    }
}
