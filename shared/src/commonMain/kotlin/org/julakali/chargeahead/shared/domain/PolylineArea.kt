package org.julakali.chargeahead.shared.domain

/**
 * A [bufferKm]-wide tube around a route's path — the search area used once a
 * destination is set.
 *
 * The buffer is **straight-line distance, not detour distance**.
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
     * Greatest distance from [origin] that's still part of the area — roughly
     * the route's length.
     */
    override val radiusKm: Double =
        points.maxOf { origin.distanceKmTo(it) } + bufferKm

    override val boundingBox: BoundingBox =
        BoundingBox.enclosing(points).expandedBy(bufferKm)

    override fun contains(point: LatLon): Boolean = distanceKmTo(point) <= bufferKm

    /** Unchanged: the buffer already covers the entire route. */
    override fun prefetchArea(marginKm: Double): SearchArea = this

    /** Shortest distance from the point to the route, in kilometers. */
    fun distanceKmTo(point: LatLon): Double =
        points.zipWithNext().minOf { (start, end) -> point.distanceKmToSegment(start, end) }

    /** Length of the route's path, in kilometers. */
    val lengthKm: Double
        get() = points.zipWithNext().sumOf { (start, end) -> start.distanceKmTo(end) }

    /**
     * The route from the point level with [position] onward.
     *
     * Returns `null` when the destination is effectively reached.
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

        // The perpendicular foot itself becomes the new start.
        val entryPoint = interpolate(points[bestSegment], points[bestSegment + 1], bestFraction)
        val remaining = listOf(entryPoint) + points.drop(bestSegment + 1)
        if (remaining.size < 2) return null

        val rest = PolylineArea(remaining, bufferKm)
        // On the last segment, the destination point remains duplicated.
        return if (rest.lengthKm < MINIMUM_REMAINING_KM) null else rest
    }

    /**
     * The strip as a chain of overlapping circles, for sources without line
     * support.
     *
     * Each circle fully encloses its segment; consecutive segments share a
     * waypoint.
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
                // Overlap by one waypoint.
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
        /** Below this, the destination is reached. */
        private const val MINIMUM_REMAINING_KM = 0.05

        /** Segment length for [radialCover]; small enough that a source's result cap doesn't kick in. */
        const val DEFAULT_SEGMENT_KM = 40.0
    }
}
