package org.julakali.chargeahead.shared.domain

/**
 * Two picked points along start → stops → destination, for sending only a
 * section of the trip to Maps.
 */
data class SectionSelection(
    val selecting: Boolean = false,
    val a: Int? = null,
    val b: Int? = null,
) {

    fun toggled(): SectionSelection =
        if (selecting) SectionSelection() else SectionSelection(selecting = true)

    /** Every point that goes to Maps, including the stops between the two picked ones. */
    fun includes(index: Int): Boolean = when {
        a == null -> false
        b == null -> index == a
        else -> index in minOf(a, b)..maxOf(a, b)
    }

    /** First tap fills [a], the second [b]; a third starts a fresh pair. */
    fun picked(index: Int): SectionSelection = when {
        a == null -> copy(a = index)
        b == null && index != a -> copy(b = index)
        b == null -> this
        else -> copy(a = index, b = null)
    }
}

/**
 * The directions URL for [selection] along start → stops → destination. The
 * origin stays "my location", since Maps only previews a fixed one; the
 * section's first point becomes a waypoint instead.
 */
fun TripPlan.mapsUrl(startPosition: LatLon?, selection: SectionSelection): String {
    val last = stops.size + 1
    fun point(index: Int): LatLon? = when (index) {
        0 -> startPosition ?: route.points.firstOrNull()
        last -> destination.position
        else -> stops[index - 1].site.position
    }
    val a = selection.a
    val b = selection.b
    val (from, to) = if (selection.selecting && a != null && b != null) minOf(a, b) to maxOf(a, b) else 0 to last
    return MapsHandoff.directionsUrl(
        origin = null,
        destination = point(to) ?: destination.position,
        waypoints = (maxOf(from, 1) until to).mapNotNull(::point),
    )
}
