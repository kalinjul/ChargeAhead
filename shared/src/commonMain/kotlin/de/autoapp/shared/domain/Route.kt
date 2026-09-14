package de.autoapp.shared.domain

/**
 * A stretch of a route, addressed by distance from the start rather than by an
 * index into [Route.points]: the geometry is simplified independently of this,
 * and every consumer works in kilometres from the start anyway.
 */
data class RouteSegment(
    val fromKm: Double,
    val distanceKm: Double,
    val durationMinutes: Double,
) {
    val averageSpeedKmh: Double
        get() = if (durationMinutes <= 0.0) 0.0 else distanceKm / durationMinutes * 60.0
}

/**
 * A calculated driving route.
 *
 * [points] is the simplified path, not every bend in the road: a few dozen
 * waypoints are enough for a buffer of a few kilometers, and OSRM returns a
 * 170 km highway trip with `overview=simplified` in 15 points and under a
 * kilobyte.
 *
 * [segments] is how distance and time split up along the way — the only thing
 * that tells a motorway stretch from a town one. It is empty when the route
 * service gave no breakdown, and a consumer then has nothing finer than the
 * route average to go on.
 */
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

/** Calculates routes. OSRM by default; later a self-hosted instance (ARCHITECTURE.md, open item 5). */
interface RouteEngine {
    /**
     * Throws on network or server errors; returns `null` when there's no
     * road connection between the points. The distinction matters: one is
     * worth retrying, the other isn't.
     */
    suspend fun route(from: LatLon, to: LatLon): Route?
}
