package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.FakeVehicleCatalog
import kotlinx.coroutines.flow.flowOf

import org.julakali.chargeahead.shared.domain.MapFilter

import org.julakali.chargeahead.shared.domain.BoundingBox

import org.julakali.chargeahead.shared.testDispatchers
import org.julakali.chargeahead.shared.core.CorridorPlanner
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeStops
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.destination
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.Reachability
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteStatus
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testVehicleRepository
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

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

    private val vehicles = testVehicleRepository()

    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val trips = TripRepository()

    private suspend fun setDestination(destination: Destination?) {
        trips.update { it.copy(destination = destination) }
    }
    private val fixes = MutableStateFlow<Fix?>(null)
    private val energy = MutableStateFlow<EnergyState?>(null)

    /** A store that the fetch fills with [sites], or fails while [broken]. */
    private inner class FakeRepository(var sites: List<ChargeSite>) : SiteRepository {
        override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = flowOf(emptyList())
        private val store = MutableStateFlow<List<ChargeSite>>(emptyList())
        val fetched = mutableListOf<Pair<SearchArea, Set<String>>>()
        var broken = false
        var invalidations = 0

        override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> {
            fetched += area to networkKeys
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

    private fun observer(repository: SiteRepository) =
        ChargeStopsObserver(repository, vehicles, FakeVehicleCatalog(), preferences, trips, CorridorPlanner(), testDispatchers).also {
            it(ChargeStopsObserver.Params(fixes, energy))
        }

    private val planToMunich =
        TripPlan(Route(a9, 170.0, 108.0), munich, emptyList(), driveMinutes = 108.0, chargeMinutes = 0.0, arrivalSocPercent = 40.0)

    private suspend fun ChargeStopsObserver.await(matching: (ChargeStops) -> Boolean = { true }): ChargeStops =
        withTimeout(5_000) { flow.first { it != null && matching(it) }!! }

    private suspend fun ChargeStopsObserver.awaitDone(matching: (ChargeStops) -> Boolean = { true }): ChargeStops =
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
    fun `a small movement does not search again but one over two kilometres does`() = runBlocking<Unit> {
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

        RefreshChargeStopsInteractor(repository, preferences)(RefreshChargeStopsInteractor.Params(searched.area)).getOrThrow()

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
        vehicles.setVehicle(vehicle)
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
        vehicles.setVehicle(vehicle)
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

        vehicles.setVehicle(vehicle)

        observe.await { it.stops.singleOrNull()?.reachability == Reachability.REACHABLE }
    }

    /** The minimum search radius applies below the reserve. */
    @Test
    fun `an empty battery does not make the list disappear`() = runBlocking<Unit> {
        vehicles.setVehicle(vehicle)
        energy.value = soc(2.0)
        val observe = observer(FakeRepository(listOf(site("a", 180.0, 5.0))))

        fixes.value = fix()

        assertEquals(Reachability.UNREACHABLE, observe.await { it.stops.isNotEmpty() }.stops.single().reachability)
    }

    /** The 1.2x margin: a band just beyond range is still searched and flagged. */
    @Test
    fun `just out of reach stays visible but far away does not`() = runBlocking<Unit> {
        vehicles.setVehicle(vehicle)
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
        preferences.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity")))
        val repository = FakeRepository(emptyList())
        val observe = observer(repository)

        fixes.value = fix()

        assertTrue(observe.awaitDone().networkFilterActive)
        assertEquals(setOf("ionity"), repository.fetched.last().second)
    }

    @Test
    fun `an inactive network filter fetches everything`() = runBlocking<Unit> {
        preferences.setNetworks(NetworkPreferences(onlyPreferred = false, preferredOperators = setOf("ionity")))
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
    fun `a planned trip to the destination is searched along its route`() = runBlocking<Unit> {
        val observe = observer(FakeRepository(emptyList()))
        fixes.value = fix(nuremberg)
        observe.await()

        trips.update { it.planned(munich, planToMunich) }

        val searched = observe.await { it.routeStatus == RouteStatus.ACTIVE }
        assertEquals(2.0, assertIs<PolylineArea>(searched.area).bufferKm)
        assertEquals(munich, searched.destination)
    }

    /** One subscriber, as the ViewModel's `stateIn` is: a second one would run a second pipeline. */
    @Test
    fun `the route strip shrinks while driving`() = runBlocking<Unit> {
        trips.update { it.planned(munich, planToMunich) }
        val observe = observer(FakeRepository(emptyList()))
        val latest = observe.flow.stateIn(this)
        suspend fun await(matching: (ChargeStops) -> Boolean) =
            withTimeout(5_000) { latest.first { it != null && matching(it) }!! }
        fixes.value = fix(nuremberg, timestampMillis = 0L)
        val atStart = await { it.routeStatus == RouteStatus.ACTIVE }.area.radiusKm

        fixes.value = fix(a9[1], timestampMillis = 100_000L)
        fixes.value = fix(a9[2], timestampMillis = 200_000L)
        fixes.value = fix(a9[3], timestampMillis = 300_000L)

        await { it.area.radiusKm < atStart }
        coroutineContext.cancelChildren()
    }

    @Test
    fun `a committed trip to the destination is searched along its route`() = runBlocking<Unit> {
        trips.update { it.committed(CommittedTrip(planToMunich, startSocPercent = 60.0, committedAtEpochMillis = 0L)) }
        val observe = observer(FakeRepository(emptyList()))

        fixes.value = fix(nuremberg)

        assertIs<PolylineArea>(observe.await { it.routeStatus == RouteStatus.ACTIVE }.area)
    }

    @Test
    fun `clearing the destination switches back to the direction of travel`() = runBlocking<Unit> {
        trips.update { it.planned(munich, planToMunich) }
        val observe = observer(FakeRepository(emptyList()))
        fixes.value = fix(nuremberg)
        observe.await { it.routeStatus == RouteStatus.ACTIVE }

        setDestination(null)

        val searched = observe.await { it.routeStatus == RouteStatus.NONE }
        assertIs<SectorArea>(searched.area)
        assertNull(searched.destination)
    }

    @Test
    fun `a destination without a trip is searched in the direction of travel`() = runBlocking<Unit> {
        setDestination(munich)
        val observe = observer(FakeRepository(emptyList()))

        fixes.value = fix(nuremberg)

        val searched = observe.await { it.routeStatus == RouteStatus.UNAVAILABLE }
        assertIs<SectorArea>(searched.area)
        assertEquals(munich, searched.destination)
    }

    @Test
    fun `dismissing the plan switches back to the direction of travel`() = runBlocking<Unit> {
        trips.update { it.planned(munich, planToMunich) }
        val observe = observer(FakeRepository(emptyList()))
        fixes.value = fix(nuremberg)
        observe.await { it.routeStatus == RouteStatus.ACTIVE }

        trips.update { it.planDismissed() }

        assertIs<SectorArea>(observe.await { it.routeStatus == RouteStatus.UNAVAILABLE }.area)
    }
}
