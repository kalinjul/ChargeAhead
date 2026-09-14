package de.autoapp.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteSegmentsTest {

    private fun step(km: Double, speedKmh: Double) =
        RawStep(distanceKm = km, durationMinutes = km / speedKmh * 60.0)

    @Test
    fun `tiny maneuver steps at one speed become a single segment`() {
        val segments = RouteSegments.coalesce(List(40) { step(0.25, 45.0) }, routeDistanceKm = 10.0)

        assertEquals(1, segments.size)
        assertEquals(10.0, segments[0].distanceKm, 1e-9)
        assertEquals(45.0, segments[0].averageSpeedKmh, 1e-9)
    }

    @Test
    fun `a change of speed splits the segment`() {
        val segments = RouteSegments.coalesce(
            listOf(step(20.0, 120.0), step(20.0, 50.0), step(20.0, 120.0)),
            routeDistanceKm = 60.0,
        )

        assertEquals(3, segments.size)
        assertEquals(listOf(0.0, 20.0, 40.0), segments.map { it.fromKm })
        assertEquals(50.0, segments[1].averageSpeedKmh, 1e-6)
    }

    /** A small change is the same road, not a new stretch. */
    @Test
    fun `a small change of speed does not split`() {
        val segments = RouteSegments.coalesce(
            listOf(step(20.0, 120.0), step(20.0, 112.0)),
            routeDistanceKm = 40.0,
        )

        assertEquals(1, segments.size)
    }

    @Test
    fun `the segments tile the route without a gap`() {
        val segments = RouteSegments.coalesce(
            listOf(step(3.0, 50.0), step(0.4, 30.0), step(85.0, 125.0), step(1.6, 60.0)),
            routeDistanceKm = 90.0,
        )

        assertEquals(0.0, segments.first().fromKm, 1e-9)
        assertEquals(90.0, segments.sumOf { it.distanceKm }, 1e-9)
        segments.zipWithNext { a, b -> assertEquals(a.fromKm + a.distanceKm, b.fromKm, 1e-9) }
    }

    @Test
    fun `a short tail is folded into the segment before it`() {
        val segments = RouteSegments.coalesce(
            listOf(step(50.0, 120.0), step(0.3, 30.0)),
            routeDistanceKm = 50.3,
        )

        assertEquals(1, segments.size)
        assertEquals(50.3, segments[0].distanceKm, 1e-9)
    }

    /**
     * Segments are addressed by distance from the start, so a breakdown that
     * does not add up would shift every later lookup rather than fail loudly.
     */
    @Test
    fun `steps that do not cover the route are refused`() {
        assertTrue(RouteSegments.coalesce(listOf(step(10.0, 100.0)), routeDistanceKm = 90.0).isEmpty())
    }

    @Test
    fun `no usable steps yield no segments`() {
        assertTrue(RouteSegments.coalesce(emptyList(), routeDistanceKm = 90.0).isEmpty())
        assertTrue(
            RouteSegments.coalesce(listOf(RawStep(90.0, 0.0)), routeDistanceKm = 90.0).isEmpty(),
            "Ohne Dauer gibt es keine Geschwindigkeit, und die ist der ganze Zweck",
        )
    }

    @Test
    fun `a long route stays bounded`() {
        val steps = List(4000) { step(0.25, if (it % 2 == 0) 50.0 else 130.0) }

        val segments = RouteSegments.coalesce(steps, routeDistanceKm = 1000.0)

        assertTrue(segments.size <= 300, "zu viele Abschnitte: ${segments.size}")
        assertEquals(1000.0, segments.sumOf { it.distanceKm }, 1e-6)
    }
}
