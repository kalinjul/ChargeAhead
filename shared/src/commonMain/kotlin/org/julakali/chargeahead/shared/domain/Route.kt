package org.julakali.chargeahead.shared.domain

import kotlinx.serialization.Serializable

/** A stretch of a route, addressed by distance from the start, with the metres it climbs and drops. */
@Serializable
data class RouteSegment(
    val fromKm: Double,
    val distanceKm: Double,
    val durationMinutes: Double,
    val ascentM: Double,
    val descentM: Double,
) {
    val averageSpeedKmh: Double
        get() = if (durationMinutes <= 0.0) 0.0 else distanceKm / durationMinutes * 60.0
}

/**
 * A calculated driving route.
 *
 * [points] is simplified to within about 100 m of the road.
 *
 * [segments] is how distance and time split up along the way; empty when the
 * route service gave no breakdown.
 */
@Serializable
data class Route(
    val points: List<LatLon>,
    val distanceKm: Double,
    val durationMinutes: Double,
    val segments: List<RouteSegment> = emptyList(),
) {
    init {
        require(points.size >= 2) { "A route needs at least two points" }
    }
}

/** A charge site beside a route. */
data class RouteSite(
    val site: ChargeSite,
    val kmFromStart: Double,
    /** Straight line to the route, not the detour. */
    val distanceFromRouteKm: Double,
)

data class RouteWithSites(val route: Route, val sites: List<RouteSite>)

/** Calculates a route together with the charge sites along it. */
interface RouteSiteSource {
    /**
     * Throws on network or server errors; returns `null` when there's no road
     * connection. The sites ascend by [RouteSite.kmFromStart]; [minPowerKw]
     * counts a site's strongest connector of any type.
     */
    suspend fun routeWithSites(
        from: LatLon,
        to: LatLon,
        bufferKm: Double,
        networkKeys: Set<String>,
        minPowerKw: Double,
    ): RouteWithSites?
}
