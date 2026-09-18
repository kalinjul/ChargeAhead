package org.julakali.chargeahead.shared.domain

import org.julakali.chargeahead.shared.core.CorridorPlanner
import org.julakali.chargeahead.shared.settings.InMemoryKeyValueStorage
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserveChargeStopsTest {

    private val start = LatLon(48.9331, 11.4779)
    private val nuremberg = LatLon(49.4521, 11.0767)
    private val munich = Destination("München Hbf", LatLon(48.1351, 11.5820))
    private val a9 = listOf(
        LatLon(49.4521, 11.0767),
        LatLon(49.19, 11.19),
        LatLon(48.93, 11.46),
        LatLon(48.55, 11.52),
        LatLon(48.1351, 11.5820),
    )

    private val vehicle = VehicleProfile(
        displayName = "Testwagen",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 18.0,
        acceptedConnectors = setOf(ConnectorType.CCS2),
    )

    private val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
    private val tripStore = TripStore()
    private val fixes = MutableStateFlow<Fix?>(null)
    private val energy = MutableStateFlow<EnergyState?>(null)

    /** A store that the fetch fills with [sites], or fails while [broken]. */
    private inner class FakeRepository(var sites: List<ChargeSite>) : SiteRepository {
        private val store = MutableStateFlow<List<ChargeSite>>(emptyList())
        val fetched = mutableListOf<Pair<SearchArea, List<Network>>>()
        var broken = false
        var invalidations = 0

        override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            fetched += area to networks
            if (broken) throw IllegalStateException("Funkloch")
            store.update { (it + sites).distinctBy(ChargeSite::id) }
            return sites
        }

        override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> =
            store.map { sites -> sites.filter { it.position in area } }

        override suspend fun invalidate() {
            invalidations++
        }
    }

    private class FixedRoute(private val route: Route?) : RouteEngine {
        var calls = 0
            private set

        override suspend fun route(from: LatLon, to: LatLon): Route? {
            calls++
            return route
        }
    }

    private fun site(id: String, bearingDeg: Double, distanceKm: Double) = ChargeSite(
        id = id,
        name = "Ladepark $id",
        operator = "TestNetz",
        position = start.destination(bearingDeg, distanceKm),
        connectors = emptyList(),
    )

    private fun fix(position: LatLon = start, timestampMillis: Long = 0L) =
        Fix(position, bearingDeg = 180.0, speedMps = 30.0, timestampMillis = timestampMillis)

    private fun soc(percent: Double) = EnergyState(percent, SoCSourceKind.MANUAL, observedAtMillis = 0L)

    private fun observer(
        repository: SiteRepository,
        router: RouteEngine = FixedRoute(Route(a9, 170.0, 108.0)),
    ) = ObserveChargeStops(repository, settings, tripStore, router, CorridorPlanner()).also {
        it(ObserveChargeStops.Params(fixes, energy))
    }

    private suspend fun ObserveChargeStops.await(matching: (ChargeStops) -> Boolean = { true }): ChargeStops =
        withTimeout(5_000) { flow.first { it != null && matching(it) }!! }

    private suspend fun ObserveChargeStops.awaitDone(matching: (ChargeStops) -> Boolean = { true }): ChargeStops =
        await { it.refill != ChargeStops.Refill.RUNNING && matching(it) }

    // --- the list ---

    @Test
    fun `before the first fix there is no list`() = runBlocking<Unit> {
        val observe = observer(FakeRepository(emptyList()))

        assertNull(withTimeout(5_000) { observe.flow.first() })
    }

    @Test
    fun `after the first fix the list is sorted by distance`() = runBlocking<Unit> {
        val observe = observer(FakeRepository(listOf(site("fern", 180.0, 60.0), site("nah", 180.0, 10.0))))

        fixes.value = fix()

        assertEquals(listOf("nah", "fern"), observe.await { it.stops.size == 2 }.stops.map { it.site.id })
    }

    @Test
    fun `a small movement does not search again, one over two kilometres does`() = runBlocking<Unit> {
        val repository = FakeRepository(listOf(site("a", 180.0, 10.0)))
        val observe = observer(repository)

        fixes.value = fix(timestampMillis = 0L)
        observe.awaitDone()
        fixes.value = fix(start.destination(180.0, 0.5), timestampMillis = 20_000L)
        val moved = start.destination(180.0, 3.0)
        fixes.value = fix(moved, timestampMillis = 30_000L)

        observe.awaitDone { it.area.origin == moved }
        assertEquals(2, repository.fetched.size)
    }

    @Test
    fun `a failed refill keeps the stored list and says so`() = runBlocking<Unit> {
        val repository = FakeRepository(listOf(site("a", 180.0, 10.0)))
        val observe = observer(repository)
        fixes.value = fix(timestampMillis = 0L)
        observe.awaitDone()

        repository.broken = true
        fixes.value = fix(start.destination(180.0, 3.0), timestampMillis = 20_000L)

        val failed = observe.await { it.refill == ChargeStops.Refill.FAILED }
        assertEquals(listOf("a"), failed.stops.map { it.site.id })
    }

    @Test
    fun `a refresh discards the stock and refills the area`() = runBlocking<Unit> {
        val repository = FakeRepository(listOf(site("a", 180.0, 10.0)))
        val observe = observer(repository)
        fixes.value = fix()
        val searched = observe.awaitDone()

        RefreshChargeStops(repository, settings)(RefreshChargeStops.Params(searched.area)).getOrThrow()

        assertEquals(1, repository.invalidations)
        assertEquals(listOf(searched.area, searched.area), repository.fetched.map { it.first })
    }

    // --- vehicle profile and charge state ---

    @Test
    fun `without a profile reachability stays unknown`() = runBlocking<Unit> {
        val observe = observer(FakeRepository(listOf(site("a", 180.0, 10.0))))

        fixes.value = fix()

        assertEquals(Reachability.UNKNOWN, observe.await { it.stops.isNotEmpty() }.stops.single().reachability)
    }

    @Test
    fun `with profile and charge reachability is classified`() = runBlocking<Unit> {
        settings.setVehicle(vehicle)
        energy.value = soc(80.0)
        val observe = observer(FakeRepository(listOf(site("a", 180.0, 10.0))))

        fixes.value = fix()

        val stop = observe.await { it.stops.isNotEmpty() }.stops.single()
        assertEquals(Reachability.REACHABLE, stop.reachability)
        assertTrue(stop.socOnArrivalPercent != null, "Arrival SoC is missing")
    }

    /** Takes effect without waiting for the next location fix. */
    @Test
    fun `a change in charge recomputes at once`() = runBlocking<Unit> {
        settings.setVehicle(vehicle)
        energy.value = soc(80.0)
        // 25 km falls within the corridor whether the battery is full or nearly
        // empty: at 15% the radius shrinks to 30 km (minimum search radius 25 km x 1.2).
        val observe = observer(FakeRepository(listOf(site("a", 180.0, 25.0))))
        fixes.value = fix()
        observe.await { it.stops.singleOrNull()?.reachability == Reachability.REACHABLE }

        // 15% - 10% reserve = 5% of 77 kWh = 3.85 kWh -> about 21 km of range.
        // Distance: 25 km straight-line x 1.25 detour factor = 31 km.
        energy.value = soc(15.0)

        observe.await { it.stops.singleOrNull()?.reachability == Reachability.UNREACHABLE }
    }

    @Test
    fun `a new profile recomputes at once`() = runBlocking<Unit> {
        energy.value = soc(80.0)
        val observe = observer(FakeRepository(listOf(site("a", 180.0, 10.0))))
        fixes.value = fix()
        observe.await { it.stops.singleOrNull()?.reachability == Reachability.UNKNOWN }

        settings.setVehicle(vehicle)

        observe.await { it.stops.singleOrNull()?.reachability == Reachability.REACHABLE }
    }

    /** The minimum search radius applies below the reserve. */
    @Test
    fun `an empty battery does not make the list disappear`() = runBlocking<Unit> {
        settings.setVehicle(vehicle)
        energy.value = soc(2.0)
        val observe = observer(FakeRepository(listOf(site("a", 180.0, 5.0))))

        fixes.value = fix()

        assertEquals(Reachability.UNREACHABLE, observe.await { it.stops.isNotEmpty() }.stops.single().reachability)
    }

    /** The 1.2x margin: a band just beyond range is still searched and flagged. */
    @Test
    fun `just out of reach stays visible, far away does not`() = runBlocking<Unit> {
        settings.setVehicle(vehicle)
        // 20% - 10% = 10% of 77 kWh = 7.7 kWh -> just under 43 km of range,
        // so a search radius of about 51 km.
        energy.value = soc(20.0)
        val observe = observer(FakeRepository(listOf(site("knapp", 180.0, 45.0), site("weitweg", 180.0, 120.0))))

        fixes.value = fix()

        val stops = observe.awaitDone().stops
        assertEquals(listOf("knapp"), stops.map { it.site.id })
        assertEquals(Reachability.UNREACHABLE, stops.single().reachability)
    }

    // --- networks ---

    @Test
    fun `an active network filter fetches the selected networks`() = runBlocking<Unit> {
        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity")))
        val repository = FakeRepository(emptyList())
        val observe = observer(repository)

        fixes.value = fix()

        assertTrue(observe.awaitDone().networkFilterActive)
        assertEquals(listOf(NetworkCatalog.byKey("ionity")), repository.fetched.last().second)
    }

    @Test
    fun `an inactive network filter fetches everything`() = runBlocking<Unit> {
        settings.setNetworks(NetworkPreferences(onlyPreferred = false, preferredOperators = setOf("ionity")))
        val repository = FakeRepository(emptyList())
        val observe = observer(repository)

        fixes.value = fix()

        observe.awaitDone()
        assertTrue(repository.fetched.last().second.isEmpty())
    }

    // --- destination and route ---

    @Test
    fun `without a destination it searches in the direction of travel`() = runBlocking<Unit> {
        val observe = observer(FakeRepository(emptyList()))

        fixes.value = fix(nuremberg)

        val searched = observe.await()
        assertIs<SectorArea>(searched.area)
        assertEquals(RouteStatus.NONE, searched.routeStatus)
    }

    @Test
    fun `with a destination it searches along the route`() = runBlocking<Unit> {
        val observe = observer(FakeRepository(emptyList()))
        fixes.value = fix(nuremberg)
        observe.await()

        settings.setDestination(munich)

        val searched = observe.await { it.routeStatus == RouteStatus.ACTIVE }
        assertEquals(2.0, assertIs<PolylineArea>(searched.area).bufferKm)
        assertEquals(munich, searched.destination)
    }

    /** One subscriber, as the ViewModel's `stateIn` is: a second one would run a second pipeline. */
    @Test
    fun `the route is fetched only once while driving`() = runBlocking<Unit> {
        val router = FixedRoute(Route(a9, 170.0, 108.0))
        val observe = observer(FakeRepository(emptyList()), router)
        val latest = observe.flow.stateIn(this)
        suspend fun await(matching: (ChargeStops) -> Boolean) =
            withTimeout(5_000) { latest.first { it != null && matching(it) }!! }
        fixes.value = fix(nuremberg, timestampMillis = 0L)
        settings.setDestination(munich)
        val atStart = await { it.routeStatus == RouteStatus.ACTIVE }.area.radiusKm

        fixes.value = fix(a9[1], timestampMillis = 100_000L)
        fixes.value = fix(a9[2], timestampMillis = 200_000L)
        fixes.value = fix(a9[3], timestampMillis = 300_000L)

        await { it.area.radiusKm < atStart }
        assertEquals(1, router.calls, "The route was computed more than once")
        coroutineContext.cancelChildren()
    }

    @Test
    fun `a planned trip to the destination brings its route along`() = runBlocking<Unit> {
        val router = FixedRoute(null)
        val route = Route(a9, 170.0, 108.0)
        tripStore.store(TripPlan(route, munich, emptyList(), driveMinutes = 108.0, chargeMinutes = 0.0, arrivalSocPercent = 40.0))
        settings.setDestination(munich)
        val observe = observer(FakeRepository(emptyList()), router)

        fixes.value = fix(nuremberg)

        assertIs<PolylineArea>(observe.await { it.routeStatus == RouteStatus.ACTIVE }.area)
        assertEquals(0, router.calls)
    }

    @Test
    fun `clearing the destination switches back to the direction of travel`() = runBlocking<Unit> {
        settings.setDestination(munich)
        val observe = observer(FakeRepository(emptyList()))
        fixes.value = fix(nuremberg)
        observe.await { it.routeStatus == RouteStatus.ACTIVE }

        settings.setDestination(null)

        val searched = observe.await { it.routeStatus == RouteStatus.NONE }
        assertIs<SectorArea>(searched.area)
        assertNull(searched.destination)
    }

    /** The list stays, but the status says the route is unavailable. */
    @Test
    fun `when routing fails the corridor remains`() = runBlocking<Unit> {
        val broken = object : RouteEngine {
            override suspend fun route(from: LatLon, to: LatLon): Route? = throw IllegalStateException("Funkloch")
        }
        settings.setDestination(munich)
        val observe = observer(FakeRepository(emptyList()), broken)

        fixes.value = fix(nuremberg)

        val searched = observe.await { it.routeStatus == RouteStatus.UNAVAILABLE }
        assertIs<SectorArea>(searched.area)
        assertEquals(munich, searched.destination)
    }

    @Test
    fun `without a road connection the corridor remains`() = runBlocking<Unit> {
        settings.setDestination(munich)
        val observe = observer(FakeRepository(emptyList()), FixedRoute(null))

        fixes.value = fix(nuremberg)

        assertIs<SectorArea>(observe.await { it.routeStatus == RouteStatus.UNAVAILABLE }.area)
    }

    /** Routing only works once it's known where the trip starts; a stored destination survives a restart. */
    @Test
    fun `a destination set before the first fix is routed from it`() = runBlocking<Unit> {
        settings.setDestination(munich)
        val observe = observer(FakeRepository(emptyList()))

        fixes.value = fix(nuremberg)

        assertIs<PolylineArea>(observe.await { it.routeStatus == RouteStatus.ACTIVE }.area)
    }
}
