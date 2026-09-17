package org.julakali.chargeahead.shared.domain

/** What a map currently shows — the search area for viewport-driven loading. */
data class ViewportArea(
    override val boundingBox: BoundingBox,
) : SearchArea {

    override val origin: LatLon = LatLon(
        lat = (boundingBox.south + boundingBox.north) / 2.0,
        lon = (boundingBox.west + boundingBox.east) / 2.0,
    )

    /** Half the diagonal — what a radial source needs to cover the whole box. */
    override val radiusKm: Double =
        origin.distanceKmTo(LatLon(boundingBox.north, boundingBox.east))

    override fun contains(point: LatLon): Boolean = boundingBox.contains(point)

    override fun prefetchArea(marginKm: Double): SearchArea =
        ViewportArea(boundingBox.expandedBy(marginKm))
}
