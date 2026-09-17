package org.julakali.chargeahead.shared.domain

/** The area that's searched for charging sites. */
sealed interface SearchArea {

    /** The origin that distances are measured from. */
    val origin: LatLon

    /** Greatest distance from [origin] that still belongs to the area, for sources with radial search. */
    val radiusKm: Double

    /**
     * Enclosing rectangle, for sources without radial search.
     *
     * Careful: a capped rectangle query is not sorted by distance.
     */
    val boundingBox: BoundingBox

    /** Is the point within the area itself (not just its box)? */
    operator fun contains(point: LatLon): Boolean

    /**
     * The area that's actually fetched — somewhat larger than the one
     * requested, so the next few kilometers are already covered.
     */
    fun prefetchArea(marginKm: Double): SearchArea
}

/**
 * Circular sector around [origin]: everything within [radiusKm] that
 * deviates by no more than [halfAngleDeg] from the heading [bearingDeg].
 *
 * A [halfAngleDeg] of 180° is the full circle.
 */
data class SectorArea(
    override val origin: LatLon,
    val bearingDeg: Double,
    val halfAngleDeg: Double,
    override val radiusKm: Double,
) : SearchArea {

    override val boundingBox: BoundingBox = computeBoundingBox()

    /** Full circle instead of a grown sector, so a change of heading doesn't force a refetch. */
    override fun prefetchArea(marginKm: Double): SearchArea =
        circle(origin, radiusKm + marginKm)

    override fun contains(point: LatLon): Boolean {
        if (origin.distanceKmTo(point) > radiusKm) return false
        if (halfAngleDeg >= 180.0) return true
        // At the origin itself, the heading to the point is undefined.
        if (origin == point) return true
        return angularDifferenceDeg(bearingDeg, origin.bearingDegTo(point)) <= halfAngleDeg
    }

    /** The box is spanned over sampled points on the sector's arc, not over the full circle. */
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

        /** Sampling step along the arc. */
        const val ARC_SAMPLE_STEP_DEG = 5.0
    }
}
