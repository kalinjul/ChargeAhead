package org.julakali.chargeahead.shared.core

import io.ktor.http.encodeURLParameter
import org.julakali.chargeahead.shared.domain.LatLon

/**
 * Builds the URLs that hand navigation to Google Maps.
 *
 * Coordinates rather than names, so Maps navigates to exactly the planned site.
 */
object MapsHandoff {

    /** Google Maps accepts at most 9 waypoints in a directions URL; the rest are dropped. */
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

    /**
     * Google Maps' own navigation URI, e.g.
     * `google.navigation:q=48.14,11.58&waypoints=51.47,6.85%7C50.1,8.6`.
     * Unlike [directionsUrl] it starts a new navigation right away instead of
     * editing the one Maps is already running.
     */
    fun navigationUri(destination: LatLon, waypoints: List<LatLon> = emptyList()): String =
        buildString {
            append("google.navigation:q=").append(destination.asPlainParam())
            val capped = waypoints.take(MAX_WAYPOINTS)
            if (capped.isNotEmpty()) {
                append("&waypoints=")
                append(capped.joinToString("%7C") { it.asPlainParam() })
            }
        }

    /** Straight to one place, from wherever the driver currently is. */
    fun navigateUrl(target: LatLon): String = directionsUrl(origin = null, destination = target)

    /** A `geo:` URI pinned at [target] and labelled [label], for whichever navigation app the driver uses. */
    fun geoUri(target: LatLon, label: String): String =
        "geo:${target.asPlainParam()}?q=${target.asPlainParam()}(${label.encodeURLParameter()})"

    private fun LatLon.asParam(): String = "$lat%2C$lon"

    private fun LatLon.asPlainParam(): String = "$lat,$lon"
}
