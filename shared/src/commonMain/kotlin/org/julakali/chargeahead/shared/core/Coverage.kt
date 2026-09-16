package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.ViewportArea
import org.julakali.chargeahead.shared.domain.distanceKmTo
import org.julakali.chargeahead.shared.domain.distanceKmToSegment
import org.julakali.chargeahead.shared.domain.interpolate
import kotlin.math.ceil
import kotlin.math.max

/**
 * Which part of the map a fetch actually answered for.
 *
 * The sources return the *shape* they were asked for — a circle, a route
 * buffer — never its bounding box. Recording the box claimed areas as
 * checked that nobody had queried, and later queries there were served an
 * incomplete store: after planning a diagonal route, ~85 % of its box counted
 * as checked. So the rule is the conservative one — recording too little
 * costs a refetch, recording too much hides chargers:
 *
 * - A fetch records only the tiles its shape **fully contains** ([tilesToRecord]).
 * - A query needs every tile its shape **touches** ([isCovered]).
 * - A route buffer is too narrow to contain a whole tile, so routes are
 *   additionally recorded as corridors, and a route query is checked piece by
 *   piece against those — or against fresh tiles, whichever covers the piece.
 */
object Coverage {

    /** The tiles a fetch of [fetched] may mark as checked. */
    fun tilesToRecord(fetched: SearchArea): List<Tiles.Tile> = when (fetched) {
        // A circle is convex, and along a parallel or a meridian the distance
        // to its center peaks at the ends — so four corners inside means the
        // whole tile is inside.
        is SectorArea -> if (fetched.halfAngleDeg >= 180.0) {
            Tiles.covering(fetched.boundingBox).filter { tile ->
                Tiles.boundsOf(tile).corners().all { fetched.origin.distanceKmTo(it) <= fetched.radiusKm }
            }
        } else {
            // Fetches are full circles (SectorArea.prefetchArea); recording
            // nothing for a partial sector is merely conservative.
            emptyList()
        }

        is ViewportArea -> Tiles.covering(fetched.boundingBox).filter { fetched.boundingBox.contains(Tiles.boundsOf(it)) }

        // The buffer around one segment is convex, so a tile counts if all its
        // corners lie within the buffer of the same segment. Below half a tile
        // height no tile can fit — the usual 2–3 km route buffer skips the loop.
        is PolylineArea -> if (2.0 * fetched.bufferKm < TILE_HEIGHT_KM) {
            emptyList()
        } else {
            val segments = fetched.points.zipWithNext()
            Tiles.covering(fetched.boundingBox).filter { tile ->
                val corners = Tiles.boundsOf(tile).corners()
                segments.any { (start, end) ->
                    corners.all { it.distanceKmToSegment(start, end) <= fetched.bufferKm }
                }
            }
        }
    }

    /**
     * Has [query] been answered completely by earlier fetches?
     *
     * [freshTiles] must hold the fresh tiles within the query's bounding box,
     * [corridors] the fresh route fetches overlapping it (only read for a
     * route query).
     */
    fun isCovered(query: SearchArea, freshTiles: Set<Tiles.Tile>, corridors: List<PolylineArea>): Boolean =
        when (query) {
            is SectorArea -> tilesTouchedBy(query).all { it in freshTiles }
            is ViewportArea -> Tiles.covering(query.boundingBox).all { it in freshTiles }
            is PolylineArea -> query.pieces().all { (start, end) ->
                isPieceCovered(start, end, query.bufferKm, freshTiles, corridors)
            }
        }

    private fun tilesTouchedBy(sector: SectorArea): List<Tiles.Tile> {
        val tiles = Tiles.covering(sector.boundingBox)
        // A partial sector's box is spanned over its arc — close enough, and
        // requiring more tiles than necessary is the safe direction.
        if (sector.halfAngleDeg < 180.0) return tiles
        return tiles.filter { tile ->
            val bounds = Tiles.boundsOf(tile)
            val nearest = LatLon(
                lat = sector.origin.lat.coerceIn(bounds.south, bounds.north),
                lon = sector.origin.lon.coerceIn(bounds.west, bounds.east),
            )
            sector.origin.distanceKmTo(nearest) <= sector.radiusKm + TOUCH_SLACK_KM
        }
    }

    /**
     * A piece is covered by a corridor when both ends lie on a single stored
     * segment within the width difference: distance to a segment is convex,
     * so nothing between the ends can stray further, and the piece's own
     * buffer then sits inside the corridor's.
     */
    private fun isPieceCovered(
        start: LatLon,
        end: LatLon,
        bufferKm: Double,
        freshTiles: Set<Tiles.Tile>,
        corridors: List<PolylineArea>,
    ): Boolean {
        val inCorridor = corridors.any { corridor ->
            corridor.points.zipWithNext().any { (from, to) ->
                val offset = max(start.distanceKmToSegment(from, to), end.distanceKmToSegment(from, to))
                offset + bufferKm <= corridor.bufferKm + SAME_LINE_TOLERANCE_KM
            }
        }
        if (inCorridor) return true
        // The box around the piece's buffer — more tiles than the buffer
        // touches, never fewer.
        val box = BoundingBox.enclosing(listOf(start, end)).expandedBy(bufferKm)
        return Tiles.covering(box).all { it in freshTiles }
    }

    /**
     * The route in pieces of at most [PIECE_KM], so a route stitched together
     * from several fetches — the planner fetches in chunks — still counts as
     * covered.
     */
    private fun PolylineArea.pieces(): List<Pair<LatLon, LatLon>> =
        points.zipWithNext().flatMap { (start, end) ->
            val count = ceil(start.distanceKmTo(end) / PIECE_KM).toInt().coerceAtLeast(1)
            (0 until count).map { index ->
                interpolate(start, end, index.toDouble() / count) to
                    interpolate(start, end, (index + 1).toDouble() / count)
            }
        }

    private fun BoundingBox.corners(): List<LatLon> = listOf(
        LatLon(south, west),
        LatLon(south, east),
        LatLon(north, west),
        LatLon(north, east),
    )

    private val TILE_HEIGHT_KM = LatLon(0.0, 0.0).distanceKmTo(LatLon(Tiles.SIZE_DEGREES, 0.0))

    /** Short enough that a gap between two fetches is never bridged by a piece. */
    private const val PIECE_KM = 1.0

    /**
     * The nearest point of a tile is taken on the degree grid, not the great
     * circle. Half a kilometre more makes up for that and only ever asks for
     * an extra tile.
     */
    private const val TOUCH_SLACK_KM = 0.5

    /**
     * Rounding room for a route checked against itself: [PolylineArea.aheadOf]
     * starts at an interpolated point that sits on the stored segment only up
     * to floating-point error. Ten metres is far below any buffer.
     */
    private const val SAME_LINE_TOLERANCE_KM = 0.01
}
