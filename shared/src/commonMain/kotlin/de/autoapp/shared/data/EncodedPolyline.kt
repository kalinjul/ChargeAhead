package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon

/** Google's Encoded Polyline Algorithm Format at precision 5. A truncated tail is dropped, not thrown on. */
internal fun decodePolyline(encoded: String): List<LatLon> {
    val points = mutableListOf<LatLon>()
    var index = 0
    var lat = 0
    var lon = 0

    while (index < encoded.length) {
        val dLat = nextValue(encoded, index) ?: return points
        val dLon = nextValue(encoded, dLat.second) ?: return points
        index = dLon.second
        lat += dLat.first
        lon += dLon.first
        points += LatLon(lat = lat / 1e5, lon = lon / 1e5)
    }
    return points
}

/** The value and the index after it, or `null` when the input ends mid-value. */
private fun nextValue(encoded: String, start: Int): Pair<Int, Int>? {
    var index = start
    var shift = 0
    var result = 0

    while (true) {
        if (index >= encoded.length) return null
        val chunk = encoded[index++].code - 63
        result = result or ((chunk and 0x1f) shl shift)
        shift += 5
        if (chunk < 0x20) break
        if (shift > 30) return null
    }

    // Zig-zag: the lowest bit carries the sign.
    val value = if (result and 1 != 0) (result shr 1).inv() else result shr 1
    return value to index
}
