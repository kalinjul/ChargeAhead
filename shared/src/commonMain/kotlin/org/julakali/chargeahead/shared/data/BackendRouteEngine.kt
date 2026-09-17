package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.RouteSegment
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.julakali.chargeahead.api.LatLonDto
import org.julakali.chargeahead.api.RouteRequest
import org.julakali.chargeahead.api.RouteResponse

/**
 * Route calculation through the ChargeAhead backend.
 *
 * POST rather than GET to keep the coordinates out of access logs.
 */
class BackendRouteEngine(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String,
) : RouteEngine {

    override suspend fun route(from: LatLon, to: LatLon): Route? {
        val response = httpClient.post("${baseUrl.trimEnd('/')}$PATH") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(RouteRequest(from = from.toDto(), to = to.toDto()))
        }

        // 204: no road connection.
        if (response.status == HttpStatusCode.NoContent) return null

        val route: RouteResponse = response.body()
        // `points` is the fallback for servers without the encoded line.
        val points = route.encodedPolyline?.let(::decodePolyline)?.takeIf { it.size >= 2 }
            ?: route.points.map { LatLon(it.lat, it.lon) }
        if (points.size < 2) return null

        return Route(
            points = points,
            distanceKm = route.distanceKm,
            durationMinutes = route.durationMinutes,
            segments = route.segments.map {
                RouteSegment(
                    fromKm = it.fromKm,
                    distanceKm = it.distanceKm,
                    durationMinutes = it.durationMinutes,
                )
            },
        )
    }

    private companion object {
        const val PATH = "/v1/route"
    }
}

private fun LatLon.toDto() = LatLonDto(lat = lat, lon = lon)
