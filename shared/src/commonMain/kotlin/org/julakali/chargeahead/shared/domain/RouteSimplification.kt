package org.julakali.chargeahead.shared.domain

/** Douglas–Peucker: drops every point within [toleranceKm] of the line kept around it. The ends always stay. */
fun List<LatLon>.simplified(toleranceKm: Double): List<LatLon> {
    if (size <= 2) return this

    val keep = BooleanArray(size)
    keep[0] = true
    keep[lastIndex] = true

    // A stack rather than recursion: a full geometry runs to tens of thousands
    // of points, and a winding one recurses about as deep.
    val pending = ArrayDeque<Pair<Int, Int>>()
    pending.addLast(0 to lastIndex)
    while (pending.isNotEmpty()) {
        val (start, end) = pending.removeLast()
        var farthest = -1
        var farthestKm = toleranceKm
        for (i in start + 1 until end) {
            val distanceKm = this[i].distanceKmToSegment(this[start], this[end])
            if (distanceKm > farthestKm) {
                farthestKm = distanceKm
                farthest = i
            }
        }
        if (farthest >= 0) {
            keep[farthest] = true
            pending.addLast(start to farthest)
            pending.addLast(farthest to end)
        }
    }
    return filterIndexed { i, _ -> keep[i] }
}
