package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.EnergyState
import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.Route
import de.autoapp.shared.domain.RouteEngine
import de.autoapp.shared.domain.RouteSegment
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.SoCSourceKind
import de.autoapp.shared.domain.VehicleProfile
import de.autoapp.shared.domain.interpolate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Issue #55: the phone's trip plan and the car's list read the same number off the same route. */
class RoutedListAgreementTest {

    private val start = LatLon(52.37, 4.90)
    private val end = LatLon(48.14, 11.58)

    private val id4 = VehicleProfile(
        displayName = "VW ID.4 Pro",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 19.5,
        acceptedConnectors = setOf(ConnectorType.CCS2),
        dcPeakPowerKw = 135.0,
    )

    // Slow first half, fast second half — where scalar and speed-aware pricing disagree.
    private val route = Route(
        points = (0 until 40).map { interpolate(start, end, it / 39.0) },
        distanceKm = 660.0,
        durationMinutes = 330.0 + 110.0,
        segments = listOf(
            RouteSegment(fromKm = 0.0, distanceKm = 330.0, durationMinutes = 330.0),
            RouteSegment(fromKm = 330.0, distanceKm = 330.0, durationMinutes = 110.0),
        ),
    )

    private val sites = (1..11).map { i ->
        val km = i * 55.0
        ChargeSite(
            id = "demo:trip-${km.toInt()}",
            name = "Ladepark km ${km.toInt()}",
            operator = "Ionity",
            position = interpolate(start, end, km / 660.0),
            connectors = listOf(Connector(ConnectorType.CCS2, 300.0, 4)),
        )
    }

    @Test
    fun `the car list shows the arrival level the trip plan computes`() = runBlocking<Unit> {
        val engine = object : RouteEngine {
            override suspend fun route(from: LatLon, to: LatLon): Route = route
        }
        val repository = object : SiteRepository {
            override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> = sites
        }
        val plan = assertIs<TripPlanResult.Planned>(
            TripPlanner(engine, repository).plan(start, Destination("München", end), id4, startSocPercent = 90.0),
        ).plan
        val firstStop = plan.stops.first()

        val provider = RoutedRouteProvider(route)
        val fix = Fix(start, bearingDeg = 120.0, speedMps = 30.0, timestampMillis = 0L)
        val listed = ChargeStopPlanner.plan(
            area = provider.searchArea(fix, rangeKm = 300.0),
            sites = sites,
            vehicle = id4,
            energy = EnergyState(90.0, SoCSourceKind.MANUAL, observedAtMillis = 0L),
            routeAhead = assertNotNull(provider.progressAt(fix)),
        ).single { it.site.id == firstStop.site.id }

        assertEquals(firstStop.kmFromStart, listed.distanceKm, 0.01)
        assertEquals(firstStop.arrivalSocPercent, listed.socOnArrivalPercent!!, 0.01)
    }
}
