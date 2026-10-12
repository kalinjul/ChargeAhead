package org.julakali.chargeahead.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TripLegsTest {

    private val start = LatLon(53.55, 9.99)
    private val munich = LatLon(48.14, 11.58)

    private fun stop(id: String, km: Double, departsAfterMinutes: Double, chargeMinutes: Double, stopMinutes: Double) = PlannedStop(
        site = ChargeSite(id = id, name = id, operator = null, position = start, connectors = emptyList()),
        kmFromStart = km,
        arrivalSocPercent = 10.0,
        departureSocPercent = 80.0,
        chargeKwh = 40.0,
        chargeMinutes = chargeMinutes,
        etaMinutesFromStart = departsAfterMinutes,
        maxPowerKw = 150.0,
        stopMinutes = stopMinutes,
    )

    private fun plan(stops: List<PlannedStop>) = TripPlan(
        route = Route(listOf(start, munich), distanceKm = 780.0, durationMinutes = 420.0),
        destination = Destination("München", munich),
        stops = stops,
        driveMinutes = 420.0,
        chargeMinutes = stops.sumOf { it.chargeMinutes },
        arrivalSocPercent = 20.0,
        stopMinutes = stops.sumOf { it.stopMinutes },
    )

    @Test
    fun withoutStops_theOnlyLegIsTheWholeDrive() {
        assertEquals(listOf(TripLeg(driveMinutes = 420.0, distanceKm = 780.0)), plan(emptyList()).legs)
    }

    /** A leg is driving only: the time at a stop belongs to neither neighbour. */
    @Test
    fun legsRunFromDepartureToArrival() {
        val plan = plan(
            listOf(
                stop("a", km = 200.0, departsAfterMinutes = 135.0, chargeMinutes = 20.0, stopMinutes = 5.0),
                stop("b", km = 500.0, departsAfterMinutes = 320.0, chargeMinutes = 25.0, stopMinutes = 5.0),
            ),
        )

        assertEquals(
            listOf(TripLeg(110.0, 200.0), TripLeg(155.0, 300.0), TripLeg(155.0, 280.0)),
            plan.legs,
        )
    }
}
