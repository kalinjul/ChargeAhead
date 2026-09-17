package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.simplified
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Route calculation via OSRM.
 *
 * **The default server is the OSRM project's public demo server**, not for
 * production use.
 *
 * `overview=full`, simplified here: OSRM's own `simplified` strays too far
 * from the road.
 */
class OsrmRouteEngine(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : RouteEngine {

    override suspend fun route(from: LatLon, to: LatLon): Route? {
        // OSRM expects longitude before latitude.
        val coordinates = "${from.lon},${from.lat};${to.lon},${to.lat}"

        val response: OsrmResponse = httpClient.get("$baseUrl/$coordinates") {
            header(HttpHeaders.UserAgent, OpenChargeMapSource.USER_AGENT)
            parameter("overview", "full")
            parameter("geometries", "geojson")
            parameter("alternatives", "false")
        }.body()

        // "NoRoute": there's no road connection.
        if (response.code != "Ok") return null

        val route = response.routes.firstOrNull() ?: return null
        val points = route.geometry.coordinates.mapNotNull { pair ->
            // GeoJSON is [longitude, latitude].
            if (pair.size < 2) null else LatLon(lat = pair[1], lon = pair[0])
        }
        if (points.size < 2) return null

        // No speed profile; planning falls back to the route's average speed.
        return Route(
            points = points.simplified(SIMPLIFY_TOLERANCE_KM),
            distanceKm = route.distance / 1000.0,
            durationMinutes = route.duration / 60.0,
        )
    }

    companion object {
        /** Public demo server. See the warning on the class. */
        const val DEFAULT_BASE_URL = "https://router.project-osrm.org/route/v1/driving"

        /** Same tolerance as the backend. */
        const val SIMPLIFY_TOLERANCE_KM = 0.1
    }
}

@Serializable
internal data class OsrmResponse(
    val code: String? = null,
    val routes: List<OsrmRoute> = emptyList(),
)

@Serializable
internal data class OsrmRoute(
    /** Meters. */
    val distance: Double = 0.0,
    /** Seconds. */
    val duration: Double = 0.0,
    val geometry: OsrmGeometry = OsrmGeometry(),
)

@Serializable
internal data class OsrmGeometry(
    @SerialName("coordinates") val coordinates: List<List<Double>> = emptyList(),
)
