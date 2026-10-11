package org.julakali.chargeahead.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineAreaBoxCoverTest {

    private val start = LatLon(48.9331, 11.4779)

    private fun BoundingBox.areaKm2(): Double =
        LatLon(south, west).distanceKmTo(LatLon(north, west)) * LatLon(south, west).distanceKmTo(LatLon(south, east))

    @Test
    fun everyPointOfTheStripLiesInSomeBox() {
        val route = PolylineArea(
            listOf(start, start.destination(135.0, 80.0), start.destination(180.0, 140.0), start.destination(200.0, 141.0)),
            bufferKm = 2.0,
        )
        val boxes = route.boxCover()

        var along = 0.0
        while (along <= 1.0) {
            route.points.zipWithNext().forEach { (from, to) ->
                val onRoute = interpolate(from, to, along)
                for (bearing in 0 until 360 step 30) {
                    val point = onRoute.destination(bearing.toDouble(), 1.99)
                    assertTrue(boxes.any { point in it }, "$point is in the strip but in no box")
                }
            }
            along += 0.01
        }
    }

    @Test
    fun aDiagonalRoutesBoxes_coverFarLessThanItsBoundingBox() {
        // Two points only: the single segment has to be cut for the boxes to help.
        val route = PolylineArea(listOf(start, start.destination(135.0, 200.0)), bufferKm = 2.0)

        val covered = route.boxCover().sumOf { it.areaKm2() }

        assertTrue(covered < route.boundingBox.areaKm2() / 5, "boxes cover $covered km²")
    }

    @Test
    fun aRouteShorterThanAStretch_isOneBox() {
        val route = PolylineArea(listOf(start, start.destination(90.0, 3.0)), bufferKm = 2.0)

        assertEquals(listOf(route.boundingBox), route.boxCover())
    }

    @Test
    fun aLongRoute_staysWithinTheBoxLimit() {
        val route = PolylineArea(listOf(start, start.destination(180.0, 2500.0)), bufferKm = 2.0)

        assertTrue(route.boxCover().size <= 101, "${route.boxCover().size} boxes")
    }
}
