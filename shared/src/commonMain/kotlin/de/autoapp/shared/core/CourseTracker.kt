package de.autoapp.shared.core

import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.bearingDegTo
import de.autoapp.shared.domain.distanceKmTo

/**
 * Fills in missing or unusable course data (ARCHITECTURE.md 5.3, step 1).
 *
 * While stationary, GPS receivers either report no course at all, or one that
 * is purely measurement noise and jumps by 180° within seconds. Either would
 * make the corridor swing around wildly. So the receiver's course is trusted
 * only above a minimum speed; below that it is derived from the distance
 * traveled, and otherwise the last known course is kept.
 *
 * Not thread-safe — one instance per feature instance, used only from within
 * its own collect loop.
 */
class CourseTracker(
    private val minSpeedMps: Double = MIN_TRUSTED_SPEED_MPS,
    private val minDistanceKm: Double = MIN_DERIVE_DISTANCE_KM,
) {

    private var lastPosition: LatLon? = null
    private var lastKnownCourseDeg: Double? = null

    /** Last trustworthy course, or `null` if there hasn't been one yet. */
    val courseDeg: Double? get() = lastKnownCourseDeg

    /** Returns the fix with the best available course. */
    fun update(fix: Fix): Fix {
        val derivedCourse = deriveCourse(fix)
        if (derivedCourse != null) lastKnownCourseDeg = derivedCourse
        lastPosition = fix.position
        return fix.copy(bearingDeg = lastKnownCourseDeg)
    }

    fun reset() {
        lastPosition = null
        lastKnownCourseDeg = null
    }

    private fun deriveCourse(fix: Fix): Double? {
        val reportedCourse = fix.bearingDeg
        val speedMps = fix.speedMps
        if (reportedCourse != null && (speedMps == null || speedMps >= minSpeedMps)) {
            return reportedCourse
        }

        val previous = lastPosition ?: return null
        if (previous.distanceKmTo(fix.position) < minDistanceKm) return null
        return previous.bearingDegTo(fix.position)
    }

    companion object {
        /** 2 m/s ≈ 7 km/h — below this, the reported course is mostly noise. */
        const val MIN_TRUSTED_SPEED_MPS = 2.0

        /** 50 m offset, the minimum worth deriving a course from two positions. */
        const val MIN_DERIVE_DISTANCE_KM = 0.05
    }
}
