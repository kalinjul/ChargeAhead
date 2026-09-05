package de.autoapp.shared.core

import de.autoapp.shared.domain.BoundingBox
import de.autoapp.shared.domain.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TilesTest {

    @Test
    fun tileIndex_followsRoundingDown() {
        assertEquals(489, Tiles.indexOf(48.9331))
        assertEquals(114, Tiles.indexOf(11.4779))
        assertEquals(0, Tiles.indexOf(0.05))
    }

    @Test
    fun tileIndex_alsoRoundsDownForNegativeValues() {
        // toInt() alone would round toward zero and collapse two tiles
        // together south of the equator.
        assertEquals(-1, Tiles.indexOf(-0.05))
        assertEquals(-340, Tiles.indexOf(-33.92))
    }

    @Test
    fun neighboringPoints_lieInTheSameTile() {
        val a = Tiles.of(LatLon(48.91, 11.41))
        val b = Tiles.of(LatLon(48.99, 11.49))

        assertEquals(a, b)
    }

    @Test
    fun oneDegreeFurther_isADifferentTile() {
        assertTrue(Tiles.of(LatLon(48.91, 11.41)) != Tiles.of(LatLon(49.01, 11.41)))
    }

    @Test
    fun covering_containsTheCornersOfTheRectangle() {
        val box = BoundingBox.of(south = 48.0, west = 11.0, north = 48.25, east = 11.25)

        val tiles = Tiles.covering(box)

        assertTrue(Tiles.of(LatLon(48.0, 11.0)) in tiles)
        assertTrue(Tiles.of(LatLon(48.25, 11.25)) in tiles)
        // 48.0..48.25 are tiles 480,481,482 — likewise for longitude.
        assertEquals(9, tiles.size)
    }

    @Test
    fun covering_aSmallRectangle_yieldsOneTile() {
        val box = BoundingBox.of(south = 48.91, west = 11.41, north = 48.92, east = 11.42)

        assertEquals(1, Tiles.covering(box).size)
    }

    @Test
    fun rangeOf_andCovering_countTheSame() {
        val box = BoundingBox.of(south = 47.3, west = 9.0, north = 50.5, east = 13.8)

        assertEquals(Tiles.rangeOf(box).count, Tiles.covering(box).size)
    }

    @Test
    fun aCorridorProducesSeveralThousandTiles() {
        // This order of magnitude is intentional: they get written in a single
        // transaction, not queried one at a time.
        val box = BoundingBox.of(south = 47.376, west = 9.053, north = 50.523, east = 13.846)

        val count = Tiles.covering(box).size

        assertTrue(count in 1000..3000, "Was $count tiles")
    }
}
