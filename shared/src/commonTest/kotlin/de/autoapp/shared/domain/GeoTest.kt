package de.autoapp.shared.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoTest {

    @Test
    fun distanceKmTo_berlinMunich_matchesKnownStraightLineDistance() {
        val berlin = LatLon(52.5200, 13.4050)
        val munich = LatLon(48.1372, 11.5755)
        val expectedKm = 504.0

        val actualKm = berlin.distanceKmTo(munich)

        val toleranceKm = expectedKm * 0.01
        assertTrue(
            abs(actualKm - expectedKm) <= toleranceKm,
            "Expected about $expectedKm km (±1%), was $actualKm km",
        )
    }

    @Test
    fun distanceKmTo_isSymmetric() {
        val a = LatLon(48.9331, 11.4779)
        val b = LatLon(48.1372, 11.5755)

        assertTrue(abs(a.distanceKmTo(b) - b.distanceKmTo(a)) < 0.0001)
    }

    @Test
    fun distanceKmTo_samePoint_isZero() {
        val point = LatLon(48.0, 11.0)
        assertTrue(point.distanceKmTo(point) < 0.0001)
    }

    @Test
    fun bearingDegTo_dueNorth_isZero() {
        val start = LatLon(48.0, 11.0)
        val north = LatLon(49.0, 11.0)

        assertTrue(abs(start.bearingDegTo(north)) < 0.001)
    }

    @Test
    fun bearingDegTo_dueEast_is90Degrees() {
        val start = LatLon(48.0, 11.0)
        val east = LatLon(48.0, 12.0)

        // On the great circle, the initial bearing due east deviates a bit from 90°;
        // over one degree of longitude at 48° latitude that's about a third of a degree.
        assertTrue(abs(start.bearingDegTo(east) - 90.0) < 0.5)
    }

    @Test
    fun bearingDegTo_dueSouth_is180Degrees() {
        val start = LatLon(48.0, 11.0)
        val south = LatLon(47.0, 11.0)

        assertTrue(abs(start.bearingDegTo(south) - 180.0) < 0.001)
    }

    @Test
    fun bearingDegTo_alwaysLiesInRangeZeroTo360() {
        val start = LatLon(48.0, 11.0)
        val west = LatLon(48.0, 10.0)

        val bearing = start.bearingDegTo(west)

        assertTrue(bearing in 0.0..360.0, "Bearing was $bearing")
        assertTrue(abs(bearing - 270.0) < 0.5)
    }

    @Test
    fun destinationAndDistance_areInverseOfEachOther() {
        val start = LatLon(48.9331, 11.4779)

        val target = start.destination(bearingDeg = 215.0, distanceKm = 120.0)

        assertTrue(abs(start.distanceKmTo(target) - 120.0) < 0.01)
        assertTrue(abs(angularDifferenceDeg(start.bearingDegTo(target), 215.0)) < 0.01)
    }

    @Test
    fun angularDifferenceDeg_acrossTheZeroMark_takesTheShortWay() {
        assertTrue(abs(angularDifferenceDeg(359.0, 1.0) - 2.0) < 0.0001)
        assertTrue(abs(angularDifferenceDeg(1.0, 359.0) - 2.0) < 0.0001)
    }

    @Test
    fun angularDifferenceDeg_isAtMost180Degrees() {
        assertTrue(abs(angularDifferenceDeg(0.0, 270.0) - 90.0) < 0.0001)
        assertTrue(abs(angularDifferenceDeg(0.0, 180.0) - 180.0) < 0.0001)
    }

    @Test
    fun normalizeDeg_bringsNegativeAndOverWoundCoursesIntoRange() {
        assertTrue(abs(normalizeDeg(-90.0) - 270.0) < 0.0001)
        assertTrue(abs(normalizeDeg(450.0) - 90.0) < 0.0001)
    }

    @Test
    fun boundingBox_containsPointsInsideAndExcludesOthers() {
        val box = BoundingBox.of(south = 48.0, west = 11.0, north = 49.0, east = 12.0)

        assertTrue(LatLon(48.5, 11.5) in box)
        assertFalse(LatLon(50.0, 11.5) in box)
        assertFalse(LatLon(48.5, 13.0) in box)
    }

    @Test
    fun boundingBox_containsBox_recognizesRealContainment() {
        val larger = BoundingBox.of(48.0, 11.0, 50.0, 13.0)
        val smaller = BoundingBox.of(48.5, 11.5, 49.5, 12.5)

        assertTrue(larger.contains(smaller))
        assertFalse(smaller.contains(larger))
    }

    @Test
    fun boundingBox_expandedBy_keepsTheDistanceInBothDirections() {
        val point = LatLon(48.5, 11.5)
        val box = BoundingBox.enclosing(listOf(point)).expandedBy(50.0)

        // The north and east edges must be about 50 km away — otherwise the
        // prefetch query would be smaller than promised. Eastward it falls short
        // by about 16 cm: expandedBy computes with the arc length along the
        // parallel, while what's measured is the shorter great-circle chord.
        // At 50 km that's three parts per million, so it doesn't matter.
        assertTrue(point.distanceKmTo(LatLon(box.north, point.lon)) >= 49.99)
        assertTrue(point.distanceKmTo(LatLon(point.lat, box.east)) >= 49.99)
    }

    @Test
    fun boundingBox_acrossTheDateLine_expandsToFullWidth() {
        // Better to over-query than to query the wrong half, see BoundingBox.
        val box = BoundingBox.enclosing(listOf(LatLon(0.0, 179.9))).expandedBy(100.0)

        assertEquals(-180.0, box.west)
        assertEquals(180.0, box.east)
    }
}
