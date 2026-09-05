package de.autoapp.shared.domain

/**
 * The area that's searched for charging sites.
 *
 * Deliberately abstract: a sector ahead in M1, a buffer along a real route
 * polyline from M5 onward (see ARCHITECTURE.md section 1.1). Because both
 * sit behind this type, the switch changes neither [RouteProvider]'s
 * signature nor anything in the car UI.
 */
sealed interface SearchArea {

    /** The origin that distances are measured from. */
    val origin: LatLon

    /**
     * Greatest distance from [origin] that still belongs to the area.
     *
     * Lives here rather than just on the sector because sources with radial
     * search ask for it — and because that's the only way the nearest
     * results come back first. A source that only understands rectangles
     * uses [boundingBox] instead.
     */
    val radiusKm: Double

    /**
     * Enclosing rectangle, for sources without radial search.
     *
     * Careful: querying a source with a result cap using a rectangle does
     * not get a distance-sorted response. On OpenChargeMap, the nearest hit
     * of a truncated rectangle response was 70 km away, even though a
     * charging site stood 2 km down the road.
     */
    val boundingBox: BoundingBox

    /** Is the point within the area itself (not just its box)? */
    operator fun contains(point: LatLon): Boolean

    /**
     * The area that's actually fetched — somewhat larger than the one
     * requested, so the next few kilometers are already covered.
     *
     * What "larger" looks like depends on the shape, which is why this
     * lives here rather than with the caller: a sector grows into a full
     * circle, so a change of heading doesn't immediately force a refetch. A
     * route buffer grows **not at all** — the route already covers the
     * whole way ahead, and while driving the area only shrinks.
     */
    fun prefetchArea(marginKm: Double): SearchArea
}

/**
 * Circular sector around [origin]: everything within [radiusKm] that
 * deviates by no more than [halfAngleDeg] from the heading [bearingDeg].
 *
 * A [halfAngleDeg] of 180° is the full circle. That's not a special case
 * but the regular state as long as no heading is known.
 */
data class SectorArea(
    override val origin: LatLon,
    val bearingDeg: Double,
    val halfAngleDeg: Double,
    override val radiusKm: Double,
) : SearchArea {

    override val boundingBox: BoundingBox = computeBoundingBox()

    /**
     * Full circle instead of a grown sector: while driving, the heading
     * rotates, and a sector that rotated along with it would end up partly
     * outside what was already fetched after every bend.
     */
    override fun prefetchArea(marginKm: Double): SearchArea =
        circle(origin, radiusKm + marginKm)

    override fun contains(point: LatLon): Boolean {
        if (origin.distanceKmTo(point) > radiusKm) return false
        if (halfAngleDeg >= 180.0) return true
        // At the origin itself, the heading to the point is undefined; the
        // point is unambiguously inside the sector nonetheless.
        if (origin == point) return true
        return angularDifferenceDeg(bearingDeg, origin.bearingDegTo(point)) <= halfAngleDeg
    }

    /**
     * The box is spanned over sampled points on the sector's arc, not over
     * the full circle. At ±35° that saves around four-fifths of the area —
     * and with it, query load on every source.
     */
    private fun computeBoundingBox(): BoundingBox {
        if (halfAngleDeg >= 180.0) {
            return BoundingBox.enclosing(listOf(origin)).expandedBy(radiusKm)
        }
        val samples = mutableListOf(origin)
        var offset = -halfAngleDeg
        while (offset < halfAngleDeg) {
            samples += origin.destination(bearingDeg + offset, radiusKm)
            offset += ARC_SAMPLE_STEP_DEG
        }
        samples += origin.destination(bearingDeg + halfAngleDeg, radiusKm)
        return BoundingBox.enclosing(samples)
    }

    companion object {
        /** Full circle around [origin] — everything within [radiusKm], with no preferred direction. */
        fun circle(origin: LatLon, radiusKm: Double): SectorArea =
            SectorArea(origin, bearingDeg = 0.0, halfAngleDeg = 180.0, radiusKm = radiusKm)

        /**
         * Sampling step along the arc. 5° keeps the error against the
         * exact enclosing rectangle under one part per thousand of the
         * radius — at 150 km that's under 150 m, well below GPS accuracy.
         */
        const val ARC_SAMPLE_STEP_DEG = 5.0
    }
}
