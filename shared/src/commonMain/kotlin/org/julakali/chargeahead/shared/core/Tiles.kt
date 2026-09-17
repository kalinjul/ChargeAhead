package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.LatLon
import kotlin.math.floor

/**
 * The 0.1° grid in which cache coverage is recorded: the unit that answers
 * *have we already checked here?*
 *
 * Tiles are not the unit queried against the network; the whole area is
 * fetched at once.
 */
object Tiles {

    /** Edge length in degrees. */
    const val SIZE_DEGREES = 0.1

    /** Tile index of a degree value: floor(degrees / 0.1). */
    fun indexOf(degrees: Double): Int = floor(degrees / SIZE_DEGREES).toInt()

    fun of(point: LatLon): Tile = Tile(indexOf(point.lat), indexOf(point.lon))

    /** All tiles touched by the rectangle. */
    fun covering(area: BoundingBox): List<Tile> {
        val range = rangeOf(area)
        val tiles = ArrayList<Tile>(range.count)
        for (lat in range.minTileLat..range.maxTileLat) {
            for (lon in range.minTileLon..range.maxTileLon) {
                tiles += Tile(lat, lon)
            }
        }
        return tiles
    }

    /** The tile bounds of a rectangle — as numbers for the database query. */
    fun rangeOf(area: BoundingBox): TileRange = TileRange(
        minTileLat = indexOf(area.south),
        maxTileLat = indexOf(area.north),
        minTileLon = indexOf(area.west),
        maxTileLon = indexOf(area.east),
    )

    /** The rectangle a tile spans, in degrees. */
    fun boundsOf(tile: Tile): BoundingBox = BoundingBox(
        south = tile.lat * SIZE_DEGREES,
        west = tile.lon * SIZE_DEGREES,
        north = (tile.lat + 1) * SIZE_DEGREES,
        east = (tile.lon + 1) * SIZE_DEGREES,
    )

    data class Tile(val lat: Int, val lon: Int)

    data class TileRange(
        val minTileLat: Int,
        val maxTileLat: Int,
        val minTileLon: Int,
        val maxTileLon: Int,
    ) {
        val count: Int
            get() = (maxTileLat - minTileLat + 1) * (maxTileLon - minTileLon + 1)
    }
}
