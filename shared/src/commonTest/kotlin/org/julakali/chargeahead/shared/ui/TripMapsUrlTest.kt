package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.core.MapsHandoff
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.TripPlan
import kotlin.test.Test
import kotlin.test.assertEquals

class TripMapsUrlTest {

    private val start = LatLon(53.55, 9.99)
    private val stopA = LatLon(52.37, 9.73)
    private val stopB = LatLon(51.31, 9.49)
    private val munich = LatLon(48.14, 11.58)

    private val plan = TripPlan(
        route = Route(listOf(start, munich), distanceKm = 780.0, durationMinutes = 420.0),
        destination = Destination("München", munich),
        stops = listOf(stop("a", stopA), stop("b", stopB)),
        driveMinutes = 420.0,
        chargeMinutes = 50.0,
        arrivalSocPercent = 20.0,
    )

    @Test
    fun withoutSelection_theWholeTripGoes_stopsAsWaypoints() {
        assertEquals(
            MapsHandoff.directionsUrl(origin = null, destination = munich, waypoints = listOf(stopA, stopB)),
            plan.mapsUrl(start, SectionSelection()),
        )
    }

    @Test
    fun aPickedSection_endsAtItsLaterPoint_withItsFirstPointAsWaypoint() {
        val selection = SectionSelection(selecting = true).picked(2).picked(1)
        assertEquals(
            MapsHandoff.directionsUrl(origin = null, destination = stopB, waypoints = listOf(stopA)),
            plan.mapsUrl(start, selection),
        )
    }

    @Test
    fun aHalfPickedSection_sendsTheWholeTrip() {
        val selection = SectionSelection(selecting = true).picked(1)
        assertEquals(plan.mapsUrl(start, SectionSelection()), plan.mapsUrl(start, selection))
    }

    @Test
    fun theStartIsNeverAWaypoint() {
        val selection = SectionSelection(selecting = true).picked(0).picked(3)
        assertEquals(
            MapsHandoff.directionsUrl(origin = null, destination = munich, waypoints = listOf(stopA, stopB)),
            plan.mapsUrl(null, selection),
        )
    }

    private fun stop(id: String, position: LatLon) = PlannedStop(
        site = ChargeSite(id = "demo:$id", name = id, operator = null, position = position, connectors = emptyList()),
        kmFromStart = 0.0,
        arrivalSocPercent = 15.0,
        departureSocPercent = 80.0,
        chargeKwh = 40.0,
        chargeMinutes = 25.0,
        etaMinutesFromStart = 0.0,
        maxPowerKw = 150.0,
    )
}
