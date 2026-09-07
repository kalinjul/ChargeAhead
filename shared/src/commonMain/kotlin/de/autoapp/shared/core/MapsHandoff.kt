package de.autoapp.shared.core

import de.autoapp.shared.domain.LatLon

/**
 * Builds the URLs that hand navigation to Google Maps.
 *
 * The app plans, Maps drives — a product decision, not a fallback (see the
 * design doc in docs/). URLs use the universal https scheme, which both
 * platforms can open without any SDK or key; coordinates rather than names,
 * so Maps navigates to exactly the planned charging site and not to a
 * same-named one two towns over.
 */
object MapsHandoff {

    /**
     * Google Maps accepts at most 9 waypoints in a directions URL. Planned
     * stops beyond that are dropped from the end of the waypoint list —
     * navigation still reaches the destination, and the app re-sends the
     * remaining stops leg by leg as the drive progresses.
     */
    const val MAX_WAYPOINTS = 9

    /** Route from [origin] via [waypoints] to [destination]. `null` origin lets Maps use the current position. */
    fun directionsUrl(origin: LatLon?, destination: LatLon, waypoints: List<LatLon> = emptyList()): String =
        buildString {
            append("https://www.google.com/maps/dir/?api=1&travelmode=driving")
            append("&destination=").append(destination.asParam())
            if (origin != null) append("&origin=").append(origin.asParam())
            val capped = waypoints.take(MAX_WAYPOINTS)
            if (capped.isNotEmpty()) {
                append("&waypoints=")
                append(capped.joinToString("%7C") { it.asParam() })
            }
        }

    /** Straight to one place, from wherever the driver currently is. */
    fun navigateUrl(target: LatLon): String = directionsUrl(origin = null, destination = target)

    private fun LatLon.asParam(): String = "$lat%2C$lon"
}
