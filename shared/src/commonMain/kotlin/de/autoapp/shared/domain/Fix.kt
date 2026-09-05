package de.autoapp.shared.domain

/**
 * A location measurement: position, heading, speed, timestamp.
 *
 * [bearingDeg] is deliberately nullable — while stationary, no GPS receiver
 * delivers a usable heading. Anyone who needs a heading runs the fix through
 * `CourseTracker` first, rather than a placeholder value being invented here.
 *
 * The timestamp is milliseconds since the Unix epoch rather than an
 * `Instant`: the time arithmetic here is limited to durations and expiry
 * checks, and a plain `Long` stays clear of the experimental time APIs whose
 * names are currently shifting between kotlinx-datetime and kotlin.time.
 */
data class Fix(
    val position: LatLon,
    val bearingDeg: Double?,
    val speedMps: Double?,
    val timestampMillis: Long,
)
