package de.autoapp.shared.domain

/**
 * A [bufferKm]-wide tube around a route's path — the search area used once a
 * destination is set.
 *
 * The counterpart to [SectorArea]: instead of a fan that mostly picks up
 * charging sites off to the sides of the direction of travel, this is exactly
 * the strip along the route. For the same Nuremberg-Munich trip, that cuts
 * 33,508 officially reported charging facilities down to 769
 * (ARCHITECTURE.md section 1.1).
 *
 * The buffer is **straight-line distance, not detour distance**. A site 1 km
 * off the highway can cost far more once you factor in reaching it via the
 * next-but-one exit. This is known and tracked as open item 6.
 */
data class PolylineArea(
    val points: List<LatLon>,
    val bufferKm: Double,
) : SearchArea {

    init {
        require(points.size >= 2) { "A route buffer needs at least two points" }
        require(bufferKm > 0.0) { "The buffer must be positive" }
    }

    /** The start of the route — distances are measured from here. */
    override val origin: LatLon = points.first()

    /**
     * Greatest distance from [origin] that's still part of the area.
     *
     * For a route, that's roughly its length — a very wide field for sources
     * that query radially. Querying with this fetches too much; sources with
     * line support use [points] and [bufferKm] instead.
     */
    override val radiusKm: Double =
        points.maxOf { origin.distanceKmTo(it) } + bufferKm

    override val boundingBox: BoundingBox =
        BoundingBox.enclosing(points).expandedBy(bufferKm)

    override fun contains(point: LatLon): Boolean = distanceKmTo(point) <= bufferKm

    /**
     * Unchanged — and that's the whole point.
     *
     * The buffer already covers the entire route to the destination; while
     * driving, it only shrinks ([aheadOf]). Widening it on top of that would
     * undo exactly what it's for: widened by 25 km, the Nuremberg-Munich trip
     * would go back up to 7,855 officially reported charging facilities
     * instead of 1,584.
     */
    override fun prefetchArea(marginKm: Double): SearchArea = this

    /** Shortest distance from the point to the route, in kilometers. */
    fun distanceKmTo(point: LatLon): Double =
        points.zipWithNext().minOf { (start, end) -> point.distanceKmToSegment(start, end) }

    /** Length of the route's path, in kilometers. */
    val lengthKm: Double
        get() = points.zipWithNext().sumOf { (start, end) -> start.distanceKmTo(end) }

    /**
     * The route from the point level with [position] onward — everything
     * already passed is dropped.
     *
     * This is why a route computed once is enough for the whole trip: it's
     * not the route that changes, only the section still ahead. No further
     * routing call is needed, and the search area shrinks on its own.
     *
     * Returns `null` when only a single point would remain — at that point
     * the destination is effectively reached.
     */
    fun aheadOf(position: LatLon): PolylineArea? {
        var bestSegment = 0
        var bestDistance = Double.MAX_VALUE
        var bestFraction = 0.0

        points.zipWithNext().forEachIndexed { index, (start, end) ->
            val distance = position.distanceKmToSegment(start, end)
            if (distance < bestDistance) {
                bestDistance = distance
                bestSegment = index
                bestFraction = position.projectionOnSegment(start, end)
            }
        }

        // The perpendicular foot itself becomes the new start, so the route
        // doesn't jump back by up to one waypoint's worth of distance.
        val entryPoint = interpolate(points[bestSegment], points[bestSegment + 1], bestFraction)
        val remaining = listOf(entryPoint) + points.drop(bestSegment + 1)
        if (remaining.size < 2) return null

        val rest = PolylineArea(remaining, bufferKm)
        // On the last segment, the destination point remains duplicated: the
        // point count alone isn't enough as a cutoff, but remaining length is.
        return if (rest.lengthKm < MINIMUM_REMAINING_KM) null else rest
    }

    /**
     * The strip as a chain of overlapping circles.
     *
     * For sources without line support: OpenChargeMap only knows rectangle
     * or radius. A route's bounding rectangle would be uselessly large — for
     * a diagonal across Germany, half the country — and a single circle
     * around the start would be just as bad. A handful of circles along the
     * route, on the other hand, is a good fit.
     *
     * Each circle fully encloses its segment: the farthest point of a
     * straight segment from the circle's center is always a waypoint, and
     * the radius is determined from those. Consecutive segments share a
     * waypoint, so no gap is left uncovered.
     */
    fun radialCover(segmentLengthKm: Double = DEFAULT_SEGMENT_KM): List<SectorArea> {
        require(segmentLengthKm > 0.0) { "Segment length must be positive" }

        val chunks = mutableListOf<List<LatLon>>()
        var current = mutableListOf(points.first())
        var accumulated = 0.0

        points.zipWithNext().forEach { (start, end) ->
            accumulated += start.distanceKmTo(end)
            current += end
            if (accumulated >= segmentLengthKm) {
                chunks += current
                // Overlap by one waypoint: otherwise a strip right in
                // between would be left unsearched.
                current = mutableListOf(end)
                accumulated = 0.0
            }
        }
        if (current.size >= 2) chunks += current
        if (chunks.isEmpty()) chunks += points

        return chunks.map { chunk ->
            val center = chunk[chunk.size / 2]
            SectorArea.circle(center, chunk.maxOf { center.distanceKmTo(it) } + bufferKm)
        }
    }

    companion object {
        /** Below this, the destination is reached — nothing left to look ahead to. */
        private const val MINIMUM_REMAINING_KM = 0.05

        /**
         * Segment length for [radialCover]. 40 km produces circles of a good
         * 20 km radius: small enough that a source's result cap doesn't kick
         * in, large enough that a highway trip is covered with a handful of
         * queries.
         */
        const val DEFAULT_SEGMENT_KM = 40.0
    }
}
