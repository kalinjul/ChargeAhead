package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteProvider
import org.julakali.chargeahead.shared.domain.SearchArea

/**
 * Search area from a real route: the strip along the stretch still ahead.
 *
 * Needs **no** routing call per location update. The route is computed once
 * when the driver sets their destination; after that, only the remaining
 * upcoming segment changes ([PolylineArea.aheadOf]).
 *
 * Falls back to [fallback] when the driver has left the route or reached the
 * destination. That's not an edge case but the normal case at the end of
 * every trip — and without the fallback the list would then be empty.
 */
class RoutedRouteProvider(
    route: Route,
    private val bufferKm: Double = DEFAULT_BUFFER_KM,
    private val maxDeviationKm: Double = DEFAULT_MAX_DEVIATION_KM,
    private val fallback: RouteProvider = CorridorRouteProvider(),
) : RouteProvider {

    private val fullArea = PolylineArea(route.points, bufferKm)
    private val measure = RouteMeasure(route)

    override fun searchArea(fix: Fix, rangeKm: Double): SearchArea =
        areaAhead(fix) ?: fallback.searchArea(fix, rangeKm)

    /**
     * The vehicle's position on the route — `null` exactly when [searchArea]
     * falls back, so a list searched in the corridor is never priced against
     * the route.
     */
    fun progressAt(fix: Fix): RouteProgress? {
        if (areaAhead(fix) == null) return null
        val projection = measure.project(fix.position)
        return RouteProgress(measure, projection.kmFromStart, projection.segmentIndex)
    }

    private fun areaAhead(fix: Fix): PolylineArea? {
        // Far off the route, it no longer tells us anything about what's
        // ahead — the direction of travel takes over again.
        if (fullArea.distanceKmTo(fix.position) > maxDeviationKm) return null
        return fullArea.aheadOf(fix.position)
    }

    companion object {
        /**
         * A 2 km buffer catches the service areas and truck stops along the
         * route while leaving out village chargers. Measured on the
         * Nürnberg–München route: 769 officially reported charging
         * facilities instead of 33,508 in the sector corridor
         * (ARCHITECTURE.md section 1.1).
         */
        const val DEFAULT_BUFFER_KM = 2.0

        /**
         * Threshold at which the route counts as abandoned. More generous
         * than the buffer so a detour to charge or a diversion doesn't
         * immediately fall back to the corridor.
         */
        const val DEFAULT_MAX_DEVIATION_KM = 10.0
    }
}
