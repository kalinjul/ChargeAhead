package de.autoapp.shared.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SectorAreaTest {

    private val origin = LatLon(48.9331, 11.4779)

    private fun sectorFacingSouth(halfAngleDeg: Double = 35.0, radiusKm: Double = 100.0) =
        SectorArea(origin, bearingDeg = 180.0, halfAngleDeg = halfAngleDeg, radiusKm = radiusKm)

    @Test
    fun contains_straightAheadInRange_liesInTheSector() {
        val sector = sectorFacingSouth()

        assertTrue(origin.destination(180.0, 50.0) in sector)
    }

    @Test
    fun contains_atTheEdgeOfTheOpeningAngle_stillLiesInTheSector() {
        val sector = sectorFacingSouth()

        assertTrue(origin.destination(180.0 - 34.0, 50.0) in sector)
        assertTrue(origin.destination(180.0 + 34.0, 50.0) in sector)
    }

    @Test
    fun contains_lateralOutsideTheAngle_fallsOut() {
        val sector = sectorFacingSouth()

        assertFalse(origin.destination(180.0 - 50.0, 50.0) in sector)
        assertFalse(origin.destination(180.0 + 50.0, 50.0) in sector)
    }

    @Test
    fun contains_behindTheVehicle_fallsOut() {
        val sector = sectorFacingSouth()

        assertFalse(origin.destination(0.0, 20.0) in sector)
    }

    @Test
    fun contains_outsideTheRadius_fallsOut() {
        val sector = sectorFacingSouth(radiusKm = 100.0)

        assertFalse(origin.destination(180.0, 140.0) in sector)
    }

    @Test
    fun contains_theOriginItself_liesInTheSector() {
        // No bearing is defined from the origin to itself; a charging station
        // right under the vehicle must not disappear because of that.
        assertTrue(origin in sectorFacingSouth())
    }

    @Test
    fun fullCircle_alsoContainsWhatLiesBehindTheVehicle() {
        val fullCircle = SectorArea(origin, bearingDeg = 0.0, halfAngleDeg = 180.0, radiusKm = 100.0)

        assertTrue(origin.destination(180.0, 50.0) in fullCircle)
        assertTrue(origin.destination(270.0, 50.0) in fullCircle)
        assertFalse(origin.destination(90.0, 150.0) in fullCircle)
    }

    @Test
    fun boundingBox_enclosesTheEntireSector() {
        val sector = sectorFacingSouth()
        val box = sector.boundingBox

        for (offset in -35..35) {
            val edgePoint = origin.destination(180.0 + offset, sector.radiusKm)
            assertTrue(edgePoint in box, "Edge point at ${offset}° was outside the box")
        }
        assertTrue(origin in box)
    }

    @Test
    fun boundingBox_isSignificantlySmallerThanThatOfTheFullCircle() {
        // The point of sampling an arc: less query load on each source.
        val sector = sectorFacingSouth()
        val fullCircle = SectorArea(origin, bearingDeg = 180.0, halfAngleDeg = 180.0, radiusKm = 100.0)

        val sectorArea = sector.boundingBox.let { (it.north - it.south) * (it.east - it.west) }
        val fullCircleArea = fullCircle.boundingBox.let { (it.north - it.south) * (it.east - it.west) }

        assertTrue(
            sectorArea < fullCircleArea * 0.5,
            "Sector box $sectorArea was not half as small as the full-circle box $fullCircleArea",
        )
    }
}
