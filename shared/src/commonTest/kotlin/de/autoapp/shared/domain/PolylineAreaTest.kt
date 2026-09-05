package de.autoapp.shared.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PolylineAreaTest {

    /** Rough A9 course Nürnberg -> Munich, as it would come from OSRM. */
    private val a9 = listOf(
        LatLon(49.38, 11.10),
        LatLon(49.19, 11.19),
        LatLon(49.05, 11.35),
        LatLon(48.93, 11.46),
        LatLon(48.79, 11.47),
        LatLon(48.55, 11.52),
        LatLon(48.25, 11.65),
        LatLon(48.18, 11.60),
    )

    private val route = PolylineArea(a9, bufferKm = 2.0)

    @Test
    fun aWaypoint_liesOnTheRoute() {
        assertTrue(a9[3] in route)
        assertTrue(route.distanceKmTo(a9[3]) < 0.001)
    }

    @Test
    fun justOffTheRoute_liesInTheBuffer() {
        val nearby = a9[3].destination(bearingDeg = 90.0, distanceKm = 1.5)

        assertTrue(nearby in route)
    }

    @Test
    fun fartherThanTheBuffer_fallsOut() {
        // Exactly what the sector corridor used to pick up.
        val villagePole = a9[3].destination(bearingDeg = 90.0, distanceKm = 8.0)

        assertFalse(villagePole in route)
    }

    @Test
    fun betweenTwoWaypoints_isMeasuredCorrectly() {
        // The point is halfway between two waypoints and therefore directly
        // on the line — not merely far from both endpoints.
        val midpoint = interpolate(a9[2], a9[3], 0.5)

        assertTrue(route.distanceKmTo(midpoint) < 0.5, "Was ${route.distanceKmTo(midpoint)} km")
    }

    @Test
    fun beyondTheEndOfTheRoute_isMeasuredToTheEndpoint() {
        // Distance to the last point, not to the extended line.
        val beyondMunich = a9.last().destination(bearingDeg = 180.0, distanceKm = 30.0)

        val distance = route.distanceKmTo(beyondMunich)

        assertTrue(abs(distance - 30.0) < 1.0, "Was $distance km")
    }

    @Test
    fun theBoundingBoxEnclosesTheBuffer() {
        val box = route.boundingBox

        assertTrue(a9.all { it in box })
        assertTrue(a9[3].destination(90.0, 1.9) in box)
    }

    @Test
    fun aheadOf_dropsTheDistanceAlreadyTraveled() {
        val trimmed = assertNotNull(route.aheadOf(a9[3]))

        assertTrue(trimmed.points.size < route.points.size)
        assertTrue(trimmed.origin.distanceKmTo(a9[3]) < 1.0)
        assertFalse(a9.first() in trimmed)
    }

    @Test
    fun aheadOf_keepsTheDestinationStillInTheArea() {
        val trimmed = assertNotNull(route.aheadOf(a9[3]))

        assertTrue(a9.last() in trimmed)
    }

    @Test
    fun aheadOf_shrinksTheAreaStepByStep() {
        // The reason a route computed once can carry the whole trip.
        val atStart = assertNotNull(route.aheadOf(a9.first())).radiusKm
        val atMidpoint = assertNotNull(route.aheadOf(a9[3])).radiusKm
        val nearDestination = assertNotNull(route.aheadOf(a9[6])).radiusKm

        assertTrue(atMidpoint < atStart, "$atMidpoint was not smaller than $atStart")
        assertTrue(nearDestination < atMidpoint, "$nearDestination was not smaller than $atMidpoint")
    }

    @Test
    fun aheadOf_atTheDestination_returnsNothingMore() {
        assertEquals(null, route.aheadOf(a9.last()))
    }

    @Test
    fun aheadOf_whenOffsetBesideTheRoute_stillApplies() {
        // The driver is at a rest stop 1 km off the highway.
        val offRoutePoint = a9[4].destination(bearingDeg = 270.0, distanceKm = 1.0)

        val trimmed = assertNotNull(route.aheadOf(offRoutePoint))

        assertFalse(a9.first() in trimmed)
        assertTrue(a9.last() in trimmed)
    }

    @Test
    fun aPolylineNeedsTwoPoints() {
        var thrown = false
        try {
            PolylineArea(listOf(LatLon(48.0, 11.0)), bufferKm = 2.0)
        } catch (expected: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    // --- Decomposition into circles, for sources without line-shape support ---

    @Test
    fun radialCover_coversTheEntireRoute() {
        val circles = route.radialCover()

        // Every waypoint must lie in at least one circle, otherwise a section
        // of the route would go unsearched.
        a9.forEach { point ->
            assertTrue(circles.any { point in it }, "Waypoint $point missing from all circles")
        }
    }

    @Test
    fun radialCover_alsoCoversThePointsBetweenTheWaypoints() {
        val circles = route.radialCover()

        a9.zipWithNext().forEach { (start, end) ->
            listOf(0.25, 0.5, 0.75).forEach { fraction ->
                val between = interpolate(start, end, fraction)
                assertTrue(
                    circles.any { between in it },
                    "Point at $fraction between $start and $end missing",
                )
            }
        }
    }

    @Test
    fun radialCover_alsoCoversTheBuffer() {
        val circles = route.radialCover()
        val besideRoute = a9[3].destination(bearingDeg = 90.0, distanceKm = 1.9)

        assertTrue(circles.any { besideRoute in it })
    }

    @Test
    fun radialCover_needsFewCirclesForAHighwayTrip() {
        // 170 km at a 40 km segment length: a handful of queries, not dozens.
        val circles = route.radialCover()

        assertTrue(circles.size in 2..8, "Was ${circles.size} circles")
    }

    @Test
    fun radialCover_shorterSegments_yieldMoreAndSmallerCircles() {
        val coarse = route.radialCover(segmentLengthKm = 80.0)
        val fine = route.radialCover(segmentLengthKm = 20.0)

        assertTrue(fine.size > coarse.size)
        assertTrue(fine.maxOf { it.radiusKm } < coarse.maxOf { it.radiusKm })
    }

    @Test
    fun radialCover_aVeryShortRoute_yieldsOneCircle() {
        val short = PolylineArea(listOf(LatLon(48.90, 11.40), LatLon(48.92, 11.42)), bufferKm = 2.0)

        assertEquals(1, short.radialCover().size)
    }
}
