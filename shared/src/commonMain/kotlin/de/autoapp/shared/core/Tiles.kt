package de.autoapp.shared.core

import de.autoapp.shared.domain.BoundingBox
import de.autoapp.shared.domain.LatLon
import kotlin.math.floor

/**
 * The 0.1° grid in which the cache is managed
 * (ARCHITECTURE.md section 6).
 *
 * A tile is about 11 km tall and, within Germany, a good 7 km wide. It's the
 * unit that answers: *have we already checked here?* Without that question,
 * there would be no way to tell whether an area has no charge site or whether
 * no one has ever looked — and that distinction decides whether the app is
 * allowed to show an empty list.
 *
 * Tiles are deliberately not the unit *queried* against the network: a 150 km
 * corridor touches over a thousand of them, and a thousand network requests
 * would be absurd. The whole area is fetched at once; coverage is recorded
 * tile by tile.
 */
object Tiles {

    /** Edge length in degrees. See ARCHITECTURE.md section 6. */
    const val SIZE_DEGREES = 0.1

    /** Tile index of a degree value: floor(degrees / 0.1). */
    fun indexOf(degrees: Double): Int = floor(degrees / SIZE_DEGREES).toInt()

    fun of(point: LatLon): Tile = Tile(indexOf(point.lat), indexOf(point.lon))

    /**
     * All tiles touched by the rectangle.
     *
     * For a 175 km circle that's a good 1500 of them. This is intentional,
     * not an oversight: they're written in a single transaction, which is
     * exactly what SQLite is built for.
     */
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
