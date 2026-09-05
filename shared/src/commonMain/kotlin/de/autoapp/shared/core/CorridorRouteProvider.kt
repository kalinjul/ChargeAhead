package de.autoapp.shared.core

import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.RouteProvider
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.SectorArea
import kotlin.math.min

/**
 * Corridor search (M1): a sector ahead, built from position and course.
 *
 * This stands in for the fact that neither platform exposes the active
 * navigation route (ARCHITECTURE.md section 1.1). From M5 on,
 * `RoutedRouteProvider` sits alongside it — same interface, a real polyline.
 */
class CorridorRouteProvider(
    private val halfAngleDeg: Double = DEFAULT_HALF_ANGLE_DEG,
    private val maxRadiusKm: Double = MAX_RADIUS_KM,
    private val rangeMarginFactor: Double = RANGE_MARGIN_FACTOR,
) : RouteProvider {

    override fun searchArea(fix: Fix, rangeKm: Double): SearchArea {
        val radiusKm = min(rangeKm * rangeMarginFactor, maxRadiusKm)
        val bearingDeg = fix.bearingDeg

        // Without a course, search all around instead of not at all. When
        // starting off, the car is stationary, and a sector around a guessed
        // course would likely point backward — an empty list at start is the
        // surest way to lose the driver's trust immediately.
        return if (bearingDeg == null) {
            SectorArea(fix.position, bearingDeg = 0.0, halfAngleDeg = 180.0, radiusKm = radiusKm)
        } else {
            SectorArea(fix.position, bearingDeg, halfAngleDeg, radiusKm)
        }
    }

    companion object {
        /** ±35° around the course, see ARCHITECTURE.md section 5.3. */
        const val DEFAULT_HALF_ANGLE_DEG = 35.0

        /** Upper bound on the search radius, independent of range. */
        const val MAX_RADIUS_KM = 150.0

        /** Search a bit past the range so the list doesn't cut off abruptly at the edge. */
        const val RANGE_MARGIN_FACTOR = 1.2
    }
}
