package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Route
import de.autoapp.shared.domain.RouteEngine
import de.autoapp.shared.domain.simplified
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
 * **The default server is the OSRM project's public demo server.** Its
 * operators explicitly rule out production use, and there's no guarantee on
 * availability or speed. It sits behind [RouteEngine] here so switching to a
 * self-hosted instance is a one-line configuration change — **it must be
 * replaced before any release** (ARCHITECTURE.md, open item 5).
 *
 * `overview=full`, simplified here: OSRM's own `simplified` strays almost 4 km
 * from the road on a long trip, wider than the search buffers.
 */
class OsrmRouteEngine(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : RouteEngine {

    override suspend fun route(from: LatLon, to: LatLon): Route? {
        // OSRM expects longitude before latitude, separated by a semicolon.
        val coordinates = "${from.lon},${from.lat};${to.lon},${to.lat}"

        val response: OsrmResponse = httpClient.get("$baseUrl/$coordinates") {
            header(HttpHeaders.UserAgent, OpenChargeMapSource.USER_AGENT)
            parameter("overview", "full")
            parameter("geometries", "geojson")
            parameter("alternatives", "false")
        }.body()

        // "NoRoute" means: there's no road connection. That's not an error
        // worth retrying, it's a legitimate answer.
        if (response.code != "Ok") return null

        val route = response.routes.firstOrNull() ?: return null
        val points = route.geometry.coordinates.mapNotNull { pair ->
            // GeoJSON is [longitude, latitude] — this order is the most
            // common source of bugs in anything dealing with coordinates.
            if (pair.size < 2) null else LatLon(lat = pair[1], lon = pair[0])
        }
        if (points.size < 2) return null

        // No speed profile: turning steps into segments is the backend's job,
        // and a second copy here would drift from it (#56). Planning falls back
        // to the route's average speed.
        return Route(
            points = points.simplified(SIMPLIFY_TOLERANCE_KM),
            distanceKm = route.distance / 1000.0,
            durationMinutes = route.duration / 60.0,
        )
    }

    companion object {
        /**
         * Public demo server. See the warning on the class.
         */
        const val DEFAULT_BASE_URL = "https://router.project-osrm.org/route/v1/driving"

        /** The backend's tolerance, so both paths hand the planner the same line. */
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
