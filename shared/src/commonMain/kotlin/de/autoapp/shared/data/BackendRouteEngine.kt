package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Route
import de.autoapp.shared.domain.RouteEngine
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
 * Which engine answers is the server's decision, so switching to Google
 * needs no app release. POST rather than GET because the coordinates are
 * where the driver is going and would otherwise land in access logs.
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

        // 204 is the answer "there is no road connection" and carries no
        // body. Reading one would throw where nothing is wrong.
        if (response.status == HttpStatusCode.NoContent) return null

        val route: RouteResponse = response.body()
        val points = route.points.map { LatLon(it.lat, it.lon) }
        // A route of one point is not a route; the domain type rejects it.
        if (points.size < 2) return null

        return Route(
            points = points,
            distanceKm = route.distanceKm,
            durationMinutes = route.durationMinutes,
        )
    }

    private companion object {
        const val PATH = "/v1/route"
    }
}

private fun LatLon.toDto() = LatLonDto(lat = lat, lon = lon)
