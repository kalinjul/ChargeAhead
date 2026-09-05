package de.autoapp.shared.domain

/**
 * A calculated driving route.
 *
 * [points] is the simplified path, not every bend in the road: a few dozen
 * waypoints are enough for a buffer of a few kilometers, and OSRM returns a
 * 170 km highway trip with `overview=simplified` in 15 points and under a
 * kilobyte.
 */
data class Route(
    val points: List<LatLon>,
    val distanceKm: Double,
    val durationMinutes: Double,
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
