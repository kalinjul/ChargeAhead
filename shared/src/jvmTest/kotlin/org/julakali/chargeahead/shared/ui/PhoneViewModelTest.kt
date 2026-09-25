package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.ChargeStopsState
import org.julakali.chargeahead.shared.core.CorridorPlanner
import org.julakali.chargeahead.shared.data.CachingChargePointStatusRepository
import org.julakali.chargeahead.shared.data.TiledSiteRepository
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargePointState
import org.julakali.chargeahead.shared.domain.ChargePointStatus
import org.julakali.chargeahead.shared.domain.ChargePointStatusSource
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeSiteSource
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.usecases.ChargeStopsObserver
import org.julakali.chargeahead.shared.domain.usecases.DestinationSearchObserver
import org.julakali.chargeahead.shared.domain.LiveConnectorGroup
import org.julakali.chargeahead.shared.domain.MapCharger
import org.julakali.chargeahead.shared.domain.usecases.LiveConnectorsObserver
import org.julakali.chargeahead.shared.domain.usecases.MapChargersObserver
import org.julakali.chargeahead.shared.domain.usecases.RefreshLiveConnectorsInteractor
import org.julakali.chargeahead.shared.domain.usecases.RefreshChargeStopsInteractor
import org.julakali.chargeahead.shared.domain.usecases.RefreshChargerAvailabilityInteractor
import org.julakali.chargeahead.shared.domain.usecases.RefreshMapChargersInteractor
import org.julakali.chargeahead.shared.domain.usecases.RemoveVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.SelectVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateArrivalSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteAvailability
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.TripStore
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `Dispatchers.setMain(Unconfined)` provides the Main dispatcher
 * `viewModelScope` needs and keeps writes in order.
 *
 * `uiState` only produces while someone collects; [await] subscribes and
 * waits for the first matching state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneViewModelTest {

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    /** Typing the comma in "17,8" must not be swallowed by the stored profile. */
    @Test
    fun `the form keeps what was typed while the store takes what parses`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        val viewModel = VehicleSettingsViewModel(
            settings,
            stubFeature(),
            SelectVehicleInteractor(settings),
            UpdateManualSocInteractor(settings),
        )

        viewModel.onNameChanged("Testwagen")
        viewModel.onBatteryChanged("77")
        viewModel.onConsumptionChanged("17,")

        val state = viewModel.uiState.await { it.name == "Testwagen" }
        assertEquals("77", state.battery)
        assertEquals("17,", state.consumption, "the comma must survive being written through")
        // The profile is written from a coroutine.
        assertEquals(17.0, settings.vehicle.awaitValue { it != null }?.consumptionKwhPer100Km)

        viewModel.onConsumptionChanged("17,8")
        assertEquals("17,8", viewModel.uiState.await { it.consumption == "17,8" }.consumption)
        assertEquals(
            17.8,
            settings.vehicle.awaitValue { it?.consumptionKwhPer100Km != 17.0 }?.consumptionKwhPer100Km,
        )
    }

    /** Without a usable capacity, no profile is stored at all. */
    @Test
    fun `an unparseable capacity stores no profile`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        val viewModel = VehicleSettingsViewModel(
            settings,
            stubFeature(),
            SelectVehicleInteractor(settings),
            UpdateManualSocInteractor(settings),
        )

        viewModel.onNameChanged("Testwagen")
        viewModel.onConsumptionChanged("17,8")
        viewModel.onBatteryChanged("sieben")

        assertTrue(viewModel.uiState.await { it.battery == "sieben" }.batteryInvalid)
        assertNull(settings.vehicle.first())
    }

    @Test
    fun `the garage reports what the settings hold`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        val addCar = AddCarViewModel(settings, SelectVehicleInteractor(settings))
        val garage = GarageViewModel(
            settings,
            stubFeature(),
            SelectVehicleInteractor(settings),
            RemoveVehicleInteractor(settings),
            UpdateManualSocInteractor(settings),
            UpdateArrivalSocInteractor(settings),
        )

        val preset = addCar.uiState.await { it.matches.isNotEmpty() }.matches.first()
        addCar.onPresetAdded(preset)

        val state = garage.uiState.await { it.selected != null }
        assertEquals(preset.name, state.selected?.displayName)
        assertEquals(listOf(preset.name), state.vehicles.map { it.displayName })
        assertEquals(preset.consumptionKwhPer100Km, state.selectedPresetConsumption)
        assertEquals(preset.usableBatteryKwh / preset.consumptionKwhPer100Km * 100, state.selectedFullRangeKm!!, 0.5)
        // Already owned, so the add screen stops offering it.
        assertTrue(addCar.uiState.await { preset !in it.matches }.matches.none { it.name == preset.name })
    }

    /** The stops between `a` and `b` travel as waypoints and must look selected. */
    @Test
    fun `a picked section covers every point between its ends`() {
        val section = SectionSelection(selecting = true).picked(3).picked(1)

        assertTrue(section.includes(1) && section.includes(2) && section.includes(3))
        assertTrue(!section.includes(0) && !section.includes(4))
    }

    @Test
    fun `a half-picked section covers only the point tapped so far`() {
        val section = SectionSelection(selecting = true).picked(2)

        assertTrue(section.includes(2))
        assertTrue(!section.includes(1) && !section.includes(3))
        assertTrue(!SectionSelection(selecting = true).includes(0), "nothing is selected before the first tap")
    }

    /** Issue #17: a filter change must re-run the marker query, not only a viewport change. */
    @Test
    fun `changing the minimum power reloads the map markers`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        val viewModel = homeViewModel(mapSites, settings)

        viewModel.onViewportChanged(VIEWPORT)
        // Default minimum is 150 kW, so the 50 kW site starts out hidden.
        assertEquals(listOf("demo:hpc"), viewModel.uiState.await { it.chargers.isNotEmpty() }.chargers.map { it.site.id })

        settings.setChargeFilters(ChargeFilters(minPowerKw = 50.0))
        assertEquals(
            listOf("demo:hpc", "demo:slow"),
            viewModel.uiState.await { it.chargers.size == 2 }.chargers.map { it.site.id },
        )
    }

    /** Same reasoning for the other filter the map applies. */
    @Test
    fun `picking networks reloads the map markers`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        val viewModel = homeViewModel(mapSites, settings)

        viewModel.onViewportChanged(VIEWPORT)
        viewModel.uiState.await { it.chargers.isNotEmpty() }

        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))
        assertTrue(viewModel.uiState.await { it.chargers.isEmpty() }.chargers.isEmpty())
    }

    @Test
    fun `a viewport change brings the markers' live availability`() = runBlocking {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        val site = mapSite("hpc", "Ionity", 300.0).copy(liveStatusId = "live-hpc")
        val statusSource = ChargePointStatusSource { ids ->
            ids.associateWith { listOf(ChargePointStatus(ChargePointState.AVAILABLE), ChargePointStatus(ChargePointState.OCCUPIED)) }
        }
        val viewModel = homeViewModel(listOf(site), settings, statusSource = statusSource)

        viewModel.onViewportChanged(VIEWPORT)

        val charger = viewModel.uiState.await { state -> state.chargers.any { it.availability != null } }.chargers.single()
        assertEquals(SiteAvailability.Live(free = 1, total = 2), charger.availability)
    }

    @Test
    fun `a selected charger shows its live connectors until dismissed`() = runBlocking {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        val site = mapSite("hpc", "Ionity", 300.0).copy(liveStatusId = "live-hpc")
        val statusSource = ChargePointStatusSource { ids ->
            ids.associateWith {
                listOf(
                    ChargePointStatus(ChargePointState.AVAILABLE, 300.0, listOf(ConnectorType.CCS2)),
                    ChargePointStatus(ChargePointState.OCCUPIED, 300.0, listOf(ConnectorType.CCS2)),
                )
            }
        }
        val viewModel = homeViewModel(listOf(site), settings, statusSource = statusSource)

        viewModel.onChargerSelected(MapCharger(site, 300.0))

        val live = viewModel.uiState.await { it.selectedStopLive != null }.selectedStopLive
        assertEquals(
            listOf(LiveConnectorGroup(listOf(ConnectorType.CCS2), 300.0, available = 1, occupied = 1, outOfOrder = 0, unknown = 0)),
            live,
        )

        viewModel.onSelectedStopDismissed()
        assertNull(viewModel.uiState.await { it.selectedStop == null }.selectedStopLive)
    }

    private val mapSites = listOf(
        mapSite("hpc", "Ionity", 300.0),
        mapSite("slow", "EnBW", 50.0),
    )

    private fun mapSite(id: String, operator: String, powerKw: Double) = ChargeSite(
        id = "demo:$id",
        name = id,
        operator = operator,
        position = LatLon(51.2, 6.7),
        connectors = listOf(Connector(ConnectorType.CCS2, powerKw, 2)),
    )

    private fun fixedSource(sites: List<ChargeSite>) = object : ChargeSiteSource {
        override val id = "fixed"
        override suspend fun query(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> = sites
    }

    /** On the *same* store the ViewModel gets, over an in-memory database. */
    private fun homeViewModel(
        sites: List<ChargeSite>,
        settings: PersistentSettingsStore,
        locationTimeoutMillis: Long = HomeViewModel.DEFAULT_LOCATION_TIMEOUT_MILLIS,
        statusSource: ChargePointStatusSource = ChargePointStatusSource { emptyMap() },
    ): HomeViewModel {
        val repository = TiledSiteRepository(fixedSource(sites), createChargeSiteDatabase(DatabaseFactory()), TimeProvider { 0L })
        val statuses = CachingChargePointStatusRepository(statusSource, TimeProvider { 0L })
        return HomeViewModel(
            stubFeature(),
            MapChargersObserver(repository, statuses, settings),
            RefreshMapChargersInteractor(repository, settings),
            RefreshChargerAvailabilityInteractor(repository, statuses, settings),
            LiveConnectorsObserver(statuses),
            RefreshLiveConnectorsInteractor(statuses),
            settings,
            locationTimeoutMillis,
        )
    }

    @Test
    fun `the corridor list waits for a fix and then shows the stored stops`() = runBlocking {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        val fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 1)
        val feature = ChargeStopsFeature(
            locationSource = object : LocationSource {
                override val updates: Flow<Fix> = fixes
            },
            dispatcher = Dispatchers.Unconfined,
        )
        val repository = TiledSiteRepository(fixedSource(mapSites), createChargeSiteDatabase(DatabaseFactory()), TimeProvider { 0L })
        val viewModel = CorridorViewModel(
            feature,
            ChargeStopsObserver(repository, settings, TripStore(), NoRoute, CorridorPlanner()),
            RefreshChargeStopsInteractor(repository, settings),
        )

        viewModel.uiState.await { it.phase == ChargeStopsState.Phase.WAITING_FOR_LOCATION }
        feature.start()
        fixes.emit(Fix(LatLon(51.21, 6.7), bearingDeg = null, speedMps = null, timestampMillis = 0L))

        val ready = viewModel.uiState.await { it.phase == ChargeStopsState.Phase.READY && it.stops.isNotEmpty() }
        assertEquals(setOf("demo:hpc", "demo:slow"), ready.stops.map { it.site.id }.toSet())
        feature.close()
    }

    @Test
    fun `without a fix the location button says it is searching`() = runBlocking {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        val viewModel = homeViewModel(emptyList(), settings)

        viewModel.onLocateRequested()

        // Spinner yes, hint no: the deadline is twenty seconds away.
        val searching = viewModel.uiState.await { it.searchingLocation }
        assertTrue(!searching.locationUnavailable)
    }

    @Test
    fun `past the deadline the map says why it is still empty`() = runBlocking {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        val viewModel = homeViewModel(emptyList(), settings, locationTimeoutMillis = 50L)

        viewModel.onLocateRequested()

        // The request is still running — that is the point of showing both.
        val givenUp = viewModel.uiState.await { it.locationUnavailable }
        assertTrue(givenUp.searchingLocation)
    }

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(TIMEOUT_MILLIS) { first(matching) }

    /** Same for a store flow: the writes behind it are asynchronous. */
    private suspend fun <T> Flow<T>.awaitValue(matching: (T) -> Boolean): T =
        withTimeout(TIMEOUT_MILLIS) { first(matching) }

    private fun stubFeature() = ChargeStopsFeature(
        locationSource = object : LocationSource {
            override val updates: Flow<Fix> = emptyFlow()
        },
        dispatcher = Dispatchers.Unconfined,
    )

    private object NoGeocoder : Geocoder {
        override suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> = emptyList()
    }

    private object NoRoute : RouteEngine {
        override suspend fun route(from: LatLon, to: LatLon): Route? = null
    }

    private object NoLocation : LocationSource {
        override val updates: Flow<Fix> = emptyFlow()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L

        val VIEWPORT = BoundingBox(south = 51.0, west = 6.5, north = 51.4, east = 7.0)
    }
}
