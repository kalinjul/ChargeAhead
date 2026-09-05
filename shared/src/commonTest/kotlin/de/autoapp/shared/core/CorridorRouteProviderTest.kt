package de.autoapp.shared.core

import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.SectorArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CorridorRouteProviderTest {

    private val provider = CorridorRouteProvider()
    private val position = LatLon(48.9331, 11.4779)

    private fun fix(bearingDeg: Double?) =
        Fix(position, bearingDeg, speedMps = 30.0, timestampMillis = 0L)

    @Test
    fun withABearing_createsASectorAhead() {
        val area = assertIs<SectorArea>(provider.searchArea(fix(187.0), rangeKm = 100.0))

        assertEquals(187.0, area.bearingDeg)
        assertEquals(CorridorRouteProvider.DEFAULT_HALF_ANGLE_DEG, area.halfAngleDeg)
        assertEquals(position, area.origin)
    }

    @Test
    fun withoutABearing_searchesAllAround() {
        // When starting off, the car is stationary: a sector around a guessed course
        // could easily point backward, leaving the list groundlessly empty.
        val area = assertIs<SectorArea>(provider.searchArea(fix(null), rangeKm = 100.0))

        assertEquals(180.0, area.halfAngleDeg)
    }

    @Test
    fun theRadiusExceedsTheRange() {
        val area = assertIs<SectorArea>(provider.searchArea(fix(0.0), rangeKm = 100.0))

        assertEquals(120.0, area.radiusKm)
    }

    @Test
    fun theRadiusIsCappedAtHighRange() {
        val area = assertIs<SectorArea>(provider.searchArea(fix(0.0), rangeKm = 600.0))

        assertEquals(CorridorRouteProvider.MAX_RADIUS_KM, area.radiusKm)
    }

    @Test
    fun theBoundingBoxEnclosesTheSector() {
        val area = provider.searchArea(fix(90.0), rangeKm = 100.0)

        assertTrue(position in area.boundingBox)
    }
}
