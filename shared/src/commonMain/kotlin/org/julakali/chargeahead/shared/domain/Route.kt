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

/** Calculates routes. */
interface RouteEngine {
    /** Throws on network or server errors; returns `null` when there's no road connection. */
    suspend fun route(from: LatLon, to: LatLon): Route?
}
