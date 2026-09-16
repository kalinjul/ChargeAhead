package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsumptionModelTest {

    private val consumptionKwhPer100Km = 20.0
    private val model = SpeedAwareConsumption(consumptionKwhPer100Km)

    private fun route(distanceKm: Double, averageSpeedKmh: Double, segments: List<RouteSegment> = emptyList()) = Route(
        points = listOf(LatLon(52.37, 4.90), LatLon(48.14, 11.58)),
        distanceKm = distanceKm,
        durationMinutes = distanceKm / averageSpeedKmh * 60.0,
        segments = segments,
    )

    private fun segment(fromKm: Double, distanceKm: Double, speedKmh: Double) =
        RouteSegment(fromKm, distanceKm, distanceKm / speedKmh * 60.0)

    /**
     * The calibration guard. The driver's number is an input, not a starting
     * guess: at the reference speed the model has to hand it back untouched, or
     * it has silently reinterpreted a value they set themselves.
     */
    @Test
    fun `at the reference speed the configured value comes back unchanged`() {
        val route = route(distanceKm = 200.0, averageSpeedKmh = SpeedAwareConsumption.REFERENCE_SPEED_KMH)

        assertEquals(40.0, model.energyKwh(route, 0.0, 200.0), 1e-9)
    }

    @Test
    fun `driving faster costs more and driving slower costs less`() {
        val fast = model.energyKwh(route(100.0, 130.0), 0.0, 100.0)
        val reference = model.energyKwh(route(100.0, 100.0), 0.0, 100.0)
        val slow = model.energyKwh(route(100.0, 80.0), 0.0, 100.0)

        assertTrue(fast > reference, "130 km/h must cost more than 100: $fast vs $reference")
        assertTrue(slow < reference, "80 km/h must cost less than 100: $slow vs $reference")
    }

    /** Roughly what a mid-size EV actually does between those two speeds. */
    @Test
    fun `the motorway penalty lands in a believable range`() {
        val ratio = model.energyKwh(route(100.0, 130.0), 0.0, 100.0) /
            model.energyKwh(route(100.0, 100.0), 0.0, 100.0)

        assertTrue(ratio in 1.2..1.4, "unerwarteter Faktor 130/100: $ratio")
    }

    @Test
    fun `the segments decide, not the route average`() {
        val mixed = route(
            distanceKm = 100.0,
            averageSpeedKmh = 100.0,
            segments = listOf(segment(0.0, 50.0, 60.0), segment(50.0, 50.0, 130.0)),
        )

        val perSegment = model.energyKwh(mixed, 0.0, 100.0)
        val flat = model.energyKwh(route(100.0, 100.0), 0.0, 100.0)

        assertTrue(perSegment > flat, "Ein Wechsel aus langsam und schnell kostet mehr: $perSegment vs $flat")
    }

    @Test
    fun `a stretch inside one segment is priced at that segment's speed`() {
        val mixed = route(
            distanceKm = 100.0,
            averageSpeedKmh = 100.0,
            segments = listOf(segment(0.0, 50.0, 60.0), segment(50.0, 50.0, 130.0)),
        )

        val townKwh = model.energyKwh(mixed, 10.0, 20.0)
        val motorwayKwh = model.energyKwh(mixed, 60.0, 70.0)

        assertTrue(motorwayKwh > townKwh, "10 km Autobahn müssen mehr kosten als 10 km Ortsdurchfahrt")
    }

    /** Without a breakdown there is still the route's own average to go on. */
    @Test
    fun `a route without segments falls back to its average speed`() {
        val plain = route(distanceKm = 100.0, averageSpeedKmh = SpeedAwareConsumption.REFERENCE_SPEED_KMH)
        val constant = ConstantConsumption(consumptionKwhPer100Km)

        assertEquals(constant.energyKwh(plain, 0.0, 100.0), model.energyKwh(plain, 0.0, 100.0), 1e-9)
    }

    @Test
    fun `reach is the inverse of energy`() {
        val mixed = route(
            distanceKm = 400.0,
            averageSpeedKmh = 110.0,
            segments = listOf(segment(0.0, 100.0, 70.0), segment(100.0, 300.0, 125.0)),
        )

        val budget = model.energyKwh(mixed, 20.0, 260.0)

        assertEquals(260.0, model.reachKm(mixed, 20.0, budget), 1e-6)
    }

    /**
     * Past the end of the route there is nothing left to price, and a reach
     * that stopped there would make every trip look like it just barely fits.
     */
    @Test
    fun `reach runs past the end of the route`() {
        val short = route(distanceKm = 50.0, averageSpeedKmh = 100.0)

        val reach = model.reachKm(short, 0.0, availableKwh = 40.0)

        assertEquals(200.0, reach, 1e-6)
    }

    @Test
    fun `an empty budget gets nowhere`() {
        val plain = route(100.0, 100.0)

        assertEquals(0.0, model.reachKm(plain, 0.0, 0.0), 1e-9)
        assertEquals(30.0, model.reachKm(plain, 30.0, -5.0), 1e-9)
    }

    /** A segment averaging walking pace is a traffic jam, not a driving style. */
    @Test
    fun `a crawling segment cannot blow up the estimate`() {
        val jam = route(
            distanceKm = 100.0,
            averageSpeedKmh = 90.0,
            segments = listOf(segment(0.0, 10.0, 3.0), segment(10.0, 90.0, 120.0)),
        )

        val perKm = model.energyKwh(jam, 0.0, 10.0) / 10.0 * 100.0

        assertTrue(perKm <= consumptionKwhPer100Km * 1.6, "Der Deckel muss greifen, war $perKm")
    }

    @Test
    fun `nothing is spent on a stretch of zero length`() {
        val plain = route(100.0, 100.0)

        assertEquals(0.0, model.energyKwh(plain, 40.0, 40.0), 1e-9)
        assertEquals(0.0, model.energyKwh(plain, 60.0, 40.0), 1e-9)
    }
}
