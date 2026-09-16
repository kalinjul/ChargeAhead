package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.Route
import de.autoapp.shared.domain.RouteSegment
import de.autoapp.shared.domain.RouteEngine
import de.autoapp.shared.domain.Network
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

    // Roughly Amsterdam → München; the planner only sees geometry, not a map.
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

    private fun straightRoute(pointCount: Int = 40, averageSpeedKmh: Double = 110.0, km: Double = 660.0): Route {
        val points = (0 until pointCount).map { interpolate(start, end, it / (pointCount - 1.0)) }
        return Route(points = points, distanceKm = km, durationMinutes = km / averageSpeedKmh * 60.0)
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

    private fun siteAt(route: Route, km: Double, powerKw: Double = 150.0) = ChargeSite(
        id = "demo:at-${km.toInt()}",
        name = "Ladepark km ${km.toInt()}",
        operator = "Audi",
        position = interpolate(route.points.first(), route.points.last(), km / route.distanceKm),
        connectors = listOf(Connector(ConnectorType.CCS2, powerKw, 4)),
    )

    private fun repositoryWith(sites: List<ChargeSite>): SiteRepository = object : SiteRepository {
        override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> = sites
    }

    private fun planner(route: Route?, sites: List<ChargeSite>) =
        TripPlanner(engineReturning(route), repositoryWith(sites))

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

    /** Issue #21: the driver sets what has to be left at the destination. */
    @Test
    fun `plans to arrive with the requested charge level`() = runBlocking<Unit> {
        val route = straightRoute()
        val plan = assertIs<TripPlanResult.Planned>(
            planner(route, sitesAlong(route))
                .plan(start, destination, id4, startSocPercent = 90.0, arrivalSocPercent = 50.0),
        ).plan

        // Not just "at least 50" — overshooting makes the setting undialable.
        assertTrue(
            plan.arrivalSocPercent in 49.0..51.0,
            "asked to arrive at 50 %, arrives at ${plan.arrivalSocPercent}",
        )
        // Only the trip-finishing stop may charge past the 80 % mark.
        plan.stops.dropLast(1).forEach { stop ->
            assertTrue(stop.departureSocPercent <= 80.0 + 1e-9, "intermediate stops stay fast")
        }
    }

    /** The other end of the same setting: arriving on fumes is the driver's call. */
    @Test
    fun `a low arrival level plans fewer stops than a high one`() = runBlocking<Unit> {
        val route = straightRoute()
        fun stopsFor(arrivalSoc: Double) = assertIs<TripPlanResult.Planned>(
            runBlocking {
                planner(route, sitesAlong(route))
                    .plan(start, destination, id4, startSocPercent = 90.0, arrivalSocPercent = arrivalSoc)
            },
        ).plan.stops.size

        assertTrue(stopsFor(0.0) <= stopsFor(70.0), "a full arrival cannot cost fewer stops than an empty one")
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
    fun `a nearly empty battery still gets a plan when a charger is close`() = runBlocking<Unit> {
        // 15 % is ~20 km of reach; a fixed 40 km minimum leg used to fail here.
        val route = straightRoute()
        val result = planner(route, sitesAlong(route, everyKm = 15.0))
            .plan(start, destination, id4, startSocPercent = 15.0)

        val plan = assertIs<TripPlanResult.Planned>(result).plan
        assertTrue(plan.stops.isNotEmpty())
        assertTrue(plan.stops.first().kmFromStart < 20.0, "first stop must be within the short reach")
    }

    @Test
    fun `chargers beside the route still project onto it, in driving order`() = runBlocking<Unit> {
        val route = straightRoute()
        // Chargers ~1 km off the line: the plan must use the projection onto
        // the route, not the raw position.
        val beside = sitesAlong(route).map {
            it.copy(position = LatLon(it.position.lat + 0.01, it.position.lon))
        }
        val plan = assertIs<TripPlanResult.Planned>(
            planner(route, beside).plan(start, destination, id4, startSocPercent = 90.0),
        ).plan

        assertTrue(plan.stops.isNotEmpty(), "a 660 km trip past roadside chargers still needs a stop")
        assertEquals(plan.stops.sortedBy { it.kmFromStart }, plan.stops, "projected stops stay in driving order")
        plan.stops.forEach {
            assertTrue(it.kmFromStart in 0.0..route.distanceKm, "km-from-start stays on the route")
        }
    }

    /**
     * The point of the speed profile. The same road at motorway pace costs
     * enough more energy to change the plan, and a planner that cannot see that
     * promises a trip the car does not make.
     */
    @Test
    fun `a faster route needs at least as many stops as a slow one`() = runBlocking<Unit> {
        fun stopsAt(speedKmh: Double): Int {
            val route = straightRoute(averageSpeedKmh = speedKmh)
            return assertIs<TripPlanResult.Planned>(
                runBlocking { planner(route, sitesAlong(route)).plan(start, destination, id4, startSocPercent = 90.0) },
            ).plan.stops.size
        }

        val slow = stopsAt(90.0)
        val fast = stopsAt(150.0)

        assertTrue(fast >= slow, "150 km/h darf nicht weniger Stopps brauchen als 90: $fast gegen $slow")
        assertTrue(fast > slow, "150 km/h muss teurer sein als 90: $fast gegen $slow")
    }

    /** The speed profile beats the route average where the two disagree. */
    @Test
    fun `the segments decide the plan, not the route average`() = runBlocking<Unit> {
        val flat = straightRoute(averageSpeedKmh = 110.0)
        // Same distance and same total time, but driven in two very different halves.
        val mixed = flat.copy(
            segments = listOf(
                RouteSegment(fromKm = 0.0, distanceKm = 330.0, durationMinutes = 330.0 / 75.0 * 60.0),
                RouteSegment(fromKm = 330.0, distanceKm = 330.0, durationMinutes = 330.0 / 205.0 * 60.0),
            ),
        )

        val flatPlan = assertIs<TripPlanResult.Planned>(
            planner(flat, sitesAlong(flat)).plan(start, destination, id4, startSocPercent = 90.0),
        ).plan
        val mixedPlan = assertIs<TripPlanResult.Planned>(
            planner(mixed, sitesAlong(mixed)).plan(start, destination, id4, startSocPercent = 90.0),
        ).plan

        assertTrue(
            mixedPlan.stops.first().kmFromStart > flatPlan.stops.first().kmFromStart,
            "die langsame erste Hälfte muss den ersten Stopp nach hinten schieben",
        )
    }

    /** Issue #54: clock time runs on the same speed profile as the energy. */
    @Test
    fun `stop ETAs follow the segments, not the route average`() = runBlocking<Unit> {
        // Slow first half, fast second half; the segments add up to the route's own duration.
        val mixed = straightRoute().copy(
            durationMinutes = 330.0 + 110.0,
            segments = listOf(
                RouteSegment(fromKm = 0.0, distanceKm = 330.0, durationMinutes = 330.0),
                RouteSegment(fromKm = 330.0, distanceKm = 330.0, durationMinutes = 110.0),
            ),
        )
        fun driveMinutes(fromKm: Double, toKm: Double): Double {
            val slowKm = (minOf(toKm, 330.0) - minOf(fromKm, 330.0)).coerceAtLeast(0.0)
            return slowKm * 1.0 + (toKm - fromKm - slowKm) / 3.0
        }

        val plan = assertIs<TripPlanResult.Planned>(
            planner(mixed, sitesAlong(mixed)).plan(start, destination, id4, startSocPercent = 90.0),
        ).plan

        val first = plan.stops.first()
        val averageEta = first.kmFromStart * mixed.durationMinutes / mixed.distanceKm + first.chargeMinutes
        assertTrue(
            first.etaMinutesFromStart > averageEta,
            "first stop at km ${first.kmFromStart}: ${first.etaMinutesFromStart} must be later than $averageEta",
        )
        assertEquals(driveMinutes(0.0, first.kmFromStart) + first.chargeMinutes, first.etaMinutesFromStart, 1e-6)

        val last = plan.stops.last()
        assertEquals(
            plan.totalMinutes,
            last.etaMinutesFromStart + driveMinutes(last.kmFromStart, mixed.distanceKm),
            1e-6,
            "last ETA plus the rest of the drive must add up to the total",
        )
    }

    /**
     * A route the service gave no breakdown for still has to plan. The average
     * speed is then all there is, and the result must stay in the same
     * ballpark as before the profile existed.
     */
    @Test
    fun `a route without segments still plans`() = runBlocking<Unit> {
        val route = straightRoute()
        assertTrue(route.segments.isEmpty())

        val plan = assertIs<TripPlanResult.Planned>(
            planner(route, sitesAlong(route)).plan(start, destination, id4, startSocPercent = 90.0),
        ).plan

        assertTrue(plan.stops.size in 2..4, "unerwartete Stoppzahl: ${plan.stops.size}")
        assertTrue(plan.stops.all { it.chargeMinutes > 0.0 })
    }

    /**
     * With a real curve the time depends on *where* in the battery the energy
     * goes, not just how much of it there is.
     */
    @Test
    fun `charge time follows the curve, not a flat average`() = runBlocking<Unit> {
        val route = straightRoute()
        val plan = assertIs<TripPlanResult.Planned>(
            planner(route, sitesAlong(route)).plan(start, destination, id4, startSocPercent = 90.0),
        ).plan

        plan.stops.forEach { stop ->
            val averageKw = stop.chargeKwh / stop.chargeMinutes * 60.0
            assertTrue(
                averageKw < minOf(stop.maxPowerKw, id4.dcPeakPowerKw!!) + 1e-9,
                "kein Stopp darf über der Spitzenleistung laden: $averageKw",
            )
        }
    }

    /**
     * Charging past 80 % buys the slowest part of the curve. A stop may only do
     * it when that actually delivers the arrival level the driver asked for —
     * otherwise it charges slowly *and* another stop follows anyway.
     */
    @Test
    fun `a stop that cannot reach the arrival level anyway stays under the cap`() = runBlocking<Unit> {
        val route = straightRoute()
        val plan = assertIs<TripPlanResult.Planned>(
            planner(route, sitesAlong(route))
                .plan(start, destination, id4, startSocPercent = 90.0, arrivalSocPercent = 70.0),
        ).plan

        plan.stops.dropLast(1).forEach { stop ->
            assertTrue(
                stop.departureSocPercent <= 80.0 + 1e-9,
                "Zwischenstopp bei km ${stop.kmFromStart} lädt auf ${stop.departureSocPercent} %",
            )
        }
    }

    private val model3 = VehicleProfile(
        displayName = "Tesla Model 3 LR",
        usableBatteryKwh = 75.0,
        consumptionKwhPer100Km = 15.0,
        acceptedConnectors = setOf(ConnectorType.CCS2),
    )

    /** At the reference speed 1 % of this battery is 5 km, which keeps the numbers below readable. */
    private fun planWithStopsAt300And600(routeKm: Double, firstStopPowerKw: Double = 150.0): TripPlan {
        val route = straightRoute(averageSpeedKmh = SpeedAwareConsumption.REFERENCE_SPEED_KMH, km = routeKm)
        return assertIs<TripPlanResult.Planned>(
            runBlocking {
                planner(route, listOf(siteAt(route, 300.0, firstStopPowerKw), siteAt(route, 600.0)))
                    .plan(start, destination, model3, startSocPercent = 80.0, arrivalSocPercent = 10.0)
            },
        ).plan
    }

    /**
     * Issue #68: from km 300 the rest needs 72 % plus the 10 % arrival level —
     * just past the 80 % cap. The stop at km 600 would charge for two percent.
     */
    @Test
    fun `charges the previous stop past 80 percent instead of adding a stop for a few percent`() {
        val plan = planWithStopsAt300And600(routeKm = 660.0)

        assertEquals(1, plan.stops.size, "stops: ${plan.stops.map { it.kmFromStart to it.chargeMinutes }}")
        val stop = plan.stops.single()
        assertEquals(82.0, stop.departureSocPercent, 1.0)
        assertEquals(10.0, plan.arrivalSocPercent, 1.0)
        assertEquals(stop.chargeMinutes, plan.chargeMinutes, 1e-9)
        assertEquals(
            plan.totalMinutes,
            stop.etaMinutesFromStart + (660.0 - stop.kmFromStart) / SpeedAwareConsumption.REFERENCE_SPEED_KMH * 60.0,
            0.5,
            "the stretched stop's ETA must include its longer charge",
        )
    }

    @Test
    fun `a stop that saves a long slow charge is kept`() {
        // From km 300 the rest now needs 100 % — far past what stretching may buy.
        val plan = planWithStopsAt300And600(routeKm = 750.0)

        assertEquals(2, plan.stops.size)
        assertTrue(plan.stops.first().departureSocPercent <= 80.0 + 1e-9)
        assertEquals(10.0, plan.arrivalSocPercent, 1.0)
    }

    @Test
    fun `a slow charger is not stretched when the next stop is quicker overall`() {
        // The rest needs 88 %: within the stretch limit, so only the time decides.
        assertEquals(1, planWithStopsAt300And600(routeKm = 690.0, firstStopPowerKw = 150.0).stops.size)

        // 80 → 88 % at 50 kW takes far longer than a short stop at 150 kW.
        val plan = planWithStopsAt300And600(routeKm = 690.0, firstStopPowerKw = 50.0)
        assertEquals(2, plan.stops.size)
        assertTrue(plan.stops.first().departureSocPercent <= 80.0 + 1e-9)
    }

    @Test
    fun `candidates are fetched in source-sized segments, not one giant area`() = runBlocking<Unit> {
        val route = straightRoute()
        val queriedRadii = mutableListOf<Double>()
        val repository = object : SiteRepository {
            override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> {
                queriedRadii += area.radiusKm
                return sitesAlong(route)
            }
        }
        val result = TripPlanner(engineReturning(route), repository)
            .plan(start, destination, id4, startSocPercent = 90.0)

        assertIs<TripPlanResult.Planned>(result)
        assertTrue(queriedRadii.size > 1, "a 660 km route must not be one query")
        assertTrue(
            queriedRadii.all { it < 150.0 },
            "every query must stay at corridor scale, got: $queriedRadii",
        )
    }

    @Test
    fun `an active network filter is forwarded to the repository, not applied on-device`() = runBlocking<Unit> {
        val route = straightRoute()
        val capturedNetworks = mutableListOf<List<Network>>()
        val repository = object : SiteRepository {
            override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> {
                capturedNetworks += networks
                return sitesAlong(route)
            }
        }
        val activeFilter = NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity"))
        TripPlanner(engineReturning(route), repository)
            .plan(start, destination, id4, startSocPercent = 90.0, networks = activeFilter)

        assertTrue(capturedNetworks.isNotEmpty(), "repository must have been queried")
        assertTrue(
            capturedNetworks.all { it.isNotEmpty() },
            "every segment query must carry the resolved network selection",
        )
        assertTrue(
            capturedNetworks.all { networks -> networks.any { it.key == "ionity" } },
            "the Ionity network must be in every query",
        )
    }
}
