package de.autoapp.shared.core

import de.autoapp.shared.data.DemoTariffSource
import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.Route
import de.autoapp.shared.domain.RouteEngine
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.VehicleProfile
import de.autoapp.shared.domain.interpolate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TripPlannerTest {

    // Roughly Amsterdam → München as a straight line; the planner only sees
    // the geometry, not the map, so a synthetic line is a valid route.
    private val start = LatLon(52.37, 4.90)
    private val end = LatLon(48.14, 11.58)
    private val destination = Destination("München", end)

    private val id4 = VehicleProfile(
        displayName = "VW ID.4 Pro",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 19.5,
        acceptedConnectors = setOf(ConnectorType.CCS2),
        dcPeakPowerKw = 135.0,
    )

    private fun straightRoute(pointCount: Int = 40): Route {
        val points = (0 until pointCount).map { interpolate(start, end, it / (pointCount - 1.0)) }
        val km = 660.0
        return Route(points = points, distanceKm = km, durationMinutes = km / 110.0 * 60.0)
    }

    private fun engineReturning(route: Route?): RouteEngine = object : RouteEngine {
        override suspend fun route(from: LatLon, to: LatLon): Route? = route
    }

    /** Fast chargers every ~55 km along the route, directly on it. */
    private fun sitesAlong(route: Route, powerKw: Double = 300.0, everyKm: Double = 55.0): List<ChargeSite> {
        val totalKm = route.distanceKm
        val sites = mutableListOf<ChargeSite>()
        var km = everyKm
        while (km < totalKm) {
            val fraction = km / totalKm
            sites += ChargeSite(
                id = "demo:trip-${km.toInt()}",
                name = "Ladepark km ${km.toInt()}",
                operator = "Ionity",
                position = interpolate(route.points.first(), route.points.last(), fraction),
                connectors = listOf(Connector(ConnectorType.CCS2, powerKw, 4)),
            )
            km += everyKm
        }
        return sites
    }

    private fun repositoryWith(sites: List<ChargeSite>): SiteRepository = object : SiteRepository {
        override suspend fun sitesIn(area: SearchArea): List<ChargeSite> = sites
    }

    private fun planner(route: Route?, sites: List<ChargeSite>) =
        TripPlanner(engineReturning(route), repositoryWith(sites), DemoTariffSource())

    @Test
    fun `plans a long trip with stops that reach the destination`() = runBlocking<Unit> {
        val route = straightRoute()
        val result = planner(route, sitesAlong(route)).plan(start, destination, id4, startSocPercent = 90.0)

        val plan = assertIs<TripPlanResult.Planned>(result).plan
        assertTrue(plan.stops.isNotEmpty(), "660 km on a 77 kWh battery needs at least one stop")
        assertTrue(plan.arrivalSocPercent > 0.0, "must not arrive empty")
        assertTrue(plan.chargeMinutes > 0.0)
        assertEquals(plan.stops.sortedBy { it.kmFromStart }, plan.stops, "stops must be in driving order")
        plan.stops.forEach { stop ->
            assertTrue(stop.departureSocPercent <= 80.0 + 1e-9, "never plans charging past 80 %")
            assertTrue(stop.departureSocPercent > stop.arrivalSocPercent, "every stop actually charges")
        }
    }

    @Test
    fun `short trip needs no stop`() = runBlocking<Unit> {
        val points = listOf(start, interpolate(start, end, 0.1))
        val route = Route(points, distanceKm = 66.0, durationMinutes = 40.0)
        val result = planner(route, sitesAlong(route)).plan(start, destination, id4, startSocPercent = 90.0)

        val plan = assertIs<TripPlanResult.Planned>(result).plan
        assertTrue(plan.stops.isEmpty())
        assertEquals(0.0, plan.chargeMinutes)
    }

    @Test
    fun `no route means NoRoute, not a crash`() = runBlocking<Unit> {
        val result = planner(route = null, sites = emptyList())
            .plan(start, destination, id4, startSocPercent = 90.0)
        assertIs<TripPlanResult.NoRoute>(result)
    }

    @Test
    fun `no chargers along the way is said, not papered over`() = runBlocking<Unit> {
        val route = straightRoute()
        val result = planner(route, sites = emptyList()).plan(start, destination, id4, startSocPercent = 90.0)
        assertIs<TripPlanResult.NoChargerInReach>(result)
    }

    @Test
    fun `estimated cost accompanies the plan`() = runBlocking<Unit> {
        val route = straightRoute()
        val result = planner(route, sitesAlong(route))
            .plan(start, destination, id4, startSocPercent = 90.0, activeTariffIds = setOf("ionity-passport"))

        val plan = assertIs<TripPlanResult.Planned>(result).plan
        val cost = plan.estimatedCostEuro
        assertTrue(cost != null && cost > 0.0)
        // All synthetic sites are Ionity and the Passport is active, so every
        // stop must be priced at the subscription rate, not ad-hoc.
        plan.stops.forEach { stop ->
            assertEquals(0.49, stop.quote.best?.euroPerKwh)
        }
    }
}
