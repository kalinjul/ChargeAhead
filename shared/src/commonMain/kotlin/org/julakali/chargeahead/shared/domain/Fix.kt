package org.julakali.chargeahead.shared.domain

/**
 * A location measurement: position, heading, speed, timestamp.
 *
 * [bearingDeg] is `null` while stationary; see `CourseTracker`.
 */
data class Fix(
    val position: LatLon,
    val bearingDeg: Double?,
    val speedMps: Double?,
    val timestampMillis: Long,
)
