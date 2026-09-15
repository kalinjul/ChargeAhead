package de.autoapp.shared.core

import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Route
import de.autoapp.shared.domain.distanceKmTo
import de.autoapp.shared.domain.distanceKmToSegment
import de.autoapp.shared.domain.projectionOnSegment

/**
 * Positions along a [Route], in kilometres from its start.
 *
 * [cumulativeKm] is scaled so its last entry is the route's own length.
 * [Route.points] is the simplified path, so summing it cuts every corner and
 * lands short — on a long route by kilometres. Everything that prices a stretch
 * measures in `route.distanceKm`: the reach, the destination check, and the
 * segments of the speed profile. Without the scaling, a site's km-from-start
 * would be read in one frame and spent in another.
 */
class RouteMeasure(val route: Route) {

    val cumulativeKm: List<Double> = run {
        val points = route.points
        val distances = ArrayList<Double>(points.size)
        distances += 0.0
        for (i in 1 until points.size) {
            distances += distances[i - 1] + points[i - 1].distanceKmTo(points[i])
        }
        val walked = distances.last()
        if (walked <= 0.0) distances else distances.map { it * route.distanceKm / walked }
    }

    data class Projection(val kmFromStart: Double, val distanceKm: Double, val segmentIndex: Int)

    /**
     * Projects [position] onto the segments `[fromIndex, toIndex)`, returning
     * km-from-start and perpendicular distance in a single pass. Sweeping the
     * whole route per site dominated long-trip planning, hence the range.
     */
    fun project(position: LatLon, fromIndex: Int = 0, toIndex: Int = route.points.size - 1): Projection {
        val points = route.points
        var bestKm = cumulativeKm[fromIndex]
        var bestDistance = Double.MAX_VALUE
        var bestIndex = fromIndex
        for (i in fromIndex until toIndex) {
            val distance = position.distanceKmToSegment(points[i], points[i + 1])
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
                val fraction = position.projectionOnSegment(points[i], points[i + 1])
                bestKm = cumulativeKm[i] + (cumulativeKm[i + 1] - cumulativeKm[i]) * fraction
            }
        }
        return Projection(bestKm, bestDistance, bestIndex)
    }
}

/** Where the vehicle currently is on a route. Sites are measured from [segmentIndex] onward, never behind it. */
data class RouteProgress(
    val measure: RouteMeasure,
    val kmFromStart: Double,
    val segmentIndex: Int,
)
