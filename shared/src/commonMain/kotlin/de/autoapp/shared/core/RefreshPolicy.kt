package de.autoapp.shared.core

import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.angularDifferenceDeg
import de.autoapp.shared.domain.distanceKmTo
import kotlin.math.abs

/**
 * Decides when the list is recomputed (ARCHITECTURE.md 5.4).
 *
 * A fixed interval would be wrong at both extremes: pointless recomputation
 * in traffic jams, too infrequent at highway speed. Hence distance *or*
 * time — and a clear course change overrides both, because after leaving the
 * highway the entire previous corridor is obsolete.
 */
class RefreshPolicy(
    private val minDistanceKm: Double = MIN_DISTANCE_KM,
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    private val courseChangeDeg: Double = COURSE_CHANGE_DEG,
) {

    fun shouldRecompute(previous: Fix?, current: Fix): Boolean {
        if (previous == null) return true

        val previousCourse = previous.bearingDeg
        val currentCourse = current.bearingDeg
        if (previousCourse != null && currentCourse != null &&
            angularDifferenceDeg(previousCourse, currentCourse) > courseChangeDeg
        ) {
            return true
        }

        if (previous.position.distanceKmTo(current.position) > minDistanceKm) return true

        // Absolute value, because fixes can arrive late or with a corrected
        // clock; a timestamp running backward must not block the refresh
        // indefinitely.
        return abs(current.timestampMillis - previous.timestampMillis) > minIntervalMillis
    }

    companion object {
        const val MIN_DISTANCE_KM = 2.0
        const val MIN_INTERVAL_MILLIS = 60_000L
        const val COURSE_CHANGE_DEG = 45.0
    }
}
