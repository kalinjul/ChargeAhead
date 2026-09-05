package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Route
import de.autoapp.shared.domain.RouteEngine
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
 * `overview=simplified` instead of `full`: for a two-kilometer buffer, the
 * coarse shape is enough. A 170 km highway trip comes back with 15 waypoints
 * in under a kilobyte this way — with `full` it would be thousands.
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
            parameter("overview", "simplified")
            parameter("geometries", "geojson")
            parameter("alternatives", "false")
            parameter("steps", "false")
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

        return Route(
            points = points,
            distanceKm = route.distance / 1000.0,
            durationMinutes = route.duration / 60.0,
        )
    }

    companion object {
        /**
         * Public demo server. See the warning on the class.
         */
        const val DEFAULT_BASE_URL = "https://router.project-osrm.org/route/v1/driving"
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
