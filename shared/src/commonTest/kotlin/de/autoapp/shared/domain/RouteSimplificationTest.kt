package de.autoapp.shared.domain

import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteSimplificationTest {

    @Test
    fun aStraightLineShrinksToItsEnds() {
        val line = (0..100).map { LatLon(lat = 48.0 + it * 0.01, lon = 11.0) }

        assertEquals(listOf(line.first(), line.last()), line.simplified(0.1))
    }

    /** At 48° N, 0.01° of longitude is about 740 m. */
    @Test
    fun aBendBeyondTheToleranceStays() {
        val bend = listOf(LatLon(48.0, 11.0), LatLon(48.05, 11.01), LatLon(48.1, 11.0))

        assertEquals(bend, bend.simplified(0.1))
        assertEquals(2, bend.simplified(1.0).size)
    }

    @Test
    fun noDroppedPointIsFartherThanTheTolerance() {
        val winding = (0..2000).map { LatLon(lat = 48.0 + it * 0.001, lon = 11.0 + 0.02 * sin(it / 40.0)) }

        val simplified = winding.simplified(0.1)

        assertTrue(simplified.size in 3 until winding.size, "${simplified.size} points")
        val area = PolylineArea(simplified, bufferKm = 1.0)
        val worst = winding.maxOf { area.distanceKmTo(it) }
        assertTrue(worst <= 0.1 + 1e-6, "A point is $worst km off the simplified line")
    }
}
