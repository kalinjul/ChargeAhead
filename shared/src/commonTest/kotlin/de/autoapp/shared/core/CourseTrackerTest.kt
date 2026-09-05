package de.autoapp.shared.core

import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.angularDifferenceDeg
import de.autoapp.shared.domain.destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseTrackerTest {

    private val start = LatLon(48.9331, 11.4779)

    private fun fix(
        position: LatLon = start,
        bearingDeg: Double? = null,
        speedMps: Double? = null,
        timestampMillis: Long = 0L,
    ) = Fix(position, bearingDeg, speedMps, timestampMillis)

    @Test
    fun whileMoving_theReportedCourseIsUsed() {
        val tracker = CourseTracker()

        val result = tracker.update(fix(bearingDeg = 187.0, speedMps = 30.0))

        assertEquals(187.0, result.bearingDeg)
    }

    @Test
    fun withoutSpeedData_theReportedCourseIsTrusted() {
        // Some sources report no speed at all; that must not invalidate the course.
        val tracker = CourseTracker()

        assertEquals(187.0, tracker.update(fix(bearingDeg = 187.0, speedMps = null)).bearingDeg)
    }

    @Test
    fun whileStationary_theReportedCourseIsDiscarded() {
        val tracker = CourseTracker()

        // 0.4 m/s is measurement noise, not a course.
        assertNull(tracker.update(fix(bearingDeg = 42.0, speedMps = 0.4)).bearingDeg)
    }

    @Test
    fun whileStationary_theLastKnownCourseIsKept() {
        val tracker = CourseTracker()
        tracker.update(fix(bearingDeg = 187.0, speedMps = 30.0))

        val atTheTrafficLight = tracker.update(fix(bearingDeg = 42.0, speedMps = 0.2))

        assertEquals(187.0, atTheTrafficLight.bearingDeg)
    }

    @Test
    fun withoutAReportedCourse_itIsDerivedFromTheDistanceTravelled() {
        val tracker = CourseTracker()
        tracker.update(fix(position = start))

        val twoKilometersSouth = start.destination(180.0, 2.0)
        val result = tracker.update(fix(position = twoKilometersSouth))

        val course = assertNotNull(result.bearingDeg)
        assertTrue(angularDifferenceDeg(course, 180.0) < 1.0, "Derived course was $course")
    }

    @Test
    fun withTooSmallAnOffset_noCourseIsDerived() {
        val tracker = CourseTracker()
        tracker.update(fix(position = start))

        // 10 m falls within the range of positioning inaccuracy.
        val result = tracker.update(fix(position = start.destination(180.0, 0.01)))

        assertNull(result.bearingDeg)
    }

    @Test
    fun reset_forgetsTheCourse() {
        val tracker = CourseTracker()
        tracker.update(fix(bearingDeg = 187.0, speedMps = 30.0))

        tracker.reset()

        assertNull(tracker.courseDeg)
    }
}
