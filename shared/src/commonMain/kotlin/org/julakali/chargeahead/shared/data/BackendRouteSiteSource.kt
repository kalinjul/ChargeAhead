package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteSegment
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
import org.julakali.chargeahead.api.RouteDto
import org.julakali.chargeahead.api.RouteRequest
import org.julakali.chargeahead.api.RouteResponse

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
                RouteRequest(
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

        val found: RouteResponse = response.body()
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
        const val PATH = "v1/route"
    }
}

/** `null` when the response carries less than a line. */
internal fun RouteDto.toDomain(): Route? {
    // `points` is the fallback for servers without the encoded line.
    val line = encodedPolyline?.let(::decodePolyline)?.takeIf { it.size >= 2 }
        ?: points.map { LatLon(it.lat, it.lon) }
    if (line.size < 2) return null

    return Route(
        points = line,
        distanceKm = distanceKm,
        durationMinutes = durationMinutes,
        segments = segments.map {
            RouteSegment(
                fromKm = it.fromKm,
                distanceKm = it.distanceKm,
                durationMinutes = it.durationMinutes,
                ascentM = requireNotNull(it.ascentM) { "Route segment at ${it.fromKm} km without its ascent" },
                descentM = requireNotNull(it.descentM) { "Route segment at ${it.fromKm} km without its descent" },
            )
        },
    )
}
