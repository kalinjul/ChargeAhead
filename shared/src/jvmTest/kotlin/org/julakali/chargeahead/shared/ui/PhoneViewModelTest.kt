package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.data.CachingChargePointStatusRepository
import org.julakali.chargeahead.shared.data.SiteFetchActivity
import org.julakali.chargeahead.shared.data.TiledSiteRepository
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargePointState
import org.julakali.chargeahead.shared.domain.ChargePointStatus
import org.julakali.chargeahead.shared.domain.ChargePointStatusSource
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeSiteSource
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.ObserveMapChargers
import org.julakali.chargeahead.shared.domain.RefreshChargerAvailability
import org.julakali.chargeahead.shared.domain.RefreshMapChargers
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteAvailability
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.settings.InMemoryKeyValueStorage
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val viewModel = VehicleSettingsViewModel(settings, stubFeature())

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
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val viewModel = VehicleSettingsViewModel(settings, stubFeature())

        viewModel.onNameChanged("Testwagen")
        viewModel.onConsumptionChanged("17,8")
        viewModel.onBatteryChanged("sieben")

        assertTrue(viewModel.uiState.await { it.battery == "sieben" }.batteryInvalid)
        assertNull(settings.vehicle.first())
    }

    @Test
    fun `the garage reports what the settings hold`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val addCar = AddCarViewModel(settings)
        val garage = GarageViewModel(settings, stubFeature())

        val preset = addCar.uiState.await { it.matches.isNotEmpty() }.matches.first()
        addCar.onPresetAdded(preset)

        val state = garage.uiState.await { it.selected != null }
        assertEquals(preset.name, state.selected?.displayName)
        assertEquals(listOf(preset.name), state.vehicles.map { it.displayName })
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

    /** Re-planning opens the sheet on the destination as a pick, not as typed text. */
    @Test
    fun `the plan sheet opens pre-filled with a destination`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val viewModel = PlanSheetViewModel(stubFeature(), settings)
        val destination = Destination("Hamburg", LatLon(53.55, 9.99))

        viewModel.onSheetOpened(destination)
        val prefilled = viewModel.uiState.await { it.chosen != null }
        assertEquals("Hamburg", prefilled.query)
        assertEquals(destination, prefilled.chosen)

        viewModel.onSheetOpened()
        assertEquals("", viewModel.uiState.await { it.chosen == null }.query)
    }

    /** Issue #43: the field used to keep only the name, dropping street and city. */
    @Test
    fun `a picked search result fills the field with its address`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val viewModel = PlanSheetViewModel(stubFeature(), settings)
        val place = Place(
            name = "Uebel und Gefährlich",
            description = "Uebel und Gefährlich, Feldstraße 66, 20359 Hamburg",
            position = LatLon(53.556, 9.968),
            address = Address(street = "Feldstraße 66", postalCode = "20359", town = "Hamburg"),
        )

        viewModel.onPlaceChosen(place)

        val state = viewModel.uiState.await { it.chosen != null }
        assertEquals("Uebel und Gefährlich, Feldstraße 66, 20359 Hamburg", state.query)
        assertEquals(Destination("Uebel und Gefährlich", place.position, "Feldstraße 66, 20359 Hamburg"), state.chosen)
    }

    /** Issue #17: a filter change must re-run the marker query, not only a viewport change. */
    @Test
    fun `changing the minimum power reloads the map markers`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
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
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val viewModel = homeViewModel(mapSites, settings)

        viewModel.onViewportChanged(VIEWPORT)
        viewModel.uiState.await { it.chargers.isNotEmpty() }

        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))
        assertTrue(viewModel.uiState.await { it.chargers.isEmpty() }.chargers.isEmpty())
    }

    @Test
    fun `a viewport change brings the markers' live availability`() = runBlocking {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val site = mapSite("hpc", "Ionity", 300.0).copy(liveStatusId = "live-hpc")
        val statusSource = ChargePointStatusSource { ids ->
            ids.associateWith { listOf(ChargePointStatus(ChargePointState.AVAILABLE), ChargePointStatus(ChargePointState.OCCUPIED)) }
        }
        val viewModel = homeViewModel(listOf(site), settings, statusSource = statusSource)

        viewModel.onViewportChanged(VIEWPORT)

        val charger = viewModel.uiState.await { state -> state.chargers.any { it.availability != null } }.chargers.single()
        assertEquals(SiteAvailability.Live(free = 1, total = 2), charger.availability)
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

    /** On the *same* store the ViewModel gets, over an in-memory database. */
    private fun homeViewModel(
        sites: List<ChargeSite>,
        settings: PersistentSettingsStore,
        locationTimeoutMillis: Long = HomeViewModel.DEFAULT_LOCATION_TIMEOUT_MILLIS,
        statusSource: ChargePointStatusSource? = null,
    ): HomeViewModel {
        val source = object : ChargeSiteSource {
            override val id = "demo"
            override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> = sites
        }
        val repository = TiledSiteRepository(source, createChargeSiteDatabase(DatabaseFactory()), TimeProvider { 0L })
        val statuses = CachingChargePointStatusRepository(statusSource, TimeProvider { 0L })
        return HomeViewModel(
            stubFeature(),
            ObserveMapChargers(repository, statuses, settings),
            RefreshMapChargers(repository, settings),
            RefreshChargerAvailability(repository, statuses, settings),
            settings,
            SiteFetchActivity(),
            locationTimeoutMillis,
        )
    }

    @Test
    fun `without a fix the location button says it is searching`() = runBlocking {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val viewModel = homeViewModel(emptyList(), settings)

        viewModel.onLocateRequested()

        // Spinner yes, hint no: the deadline is twenty seconds away.
        val searching = viewModel.uiState.await { it.searchingLocation }
        assertTrue(!searching.locationUnavailable)
    }

    @Test
    fun `past the deadline the map says why it is still empty`() = runBlocking {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
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
        repository = object : SiteRepository {
            override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> = emptyList()
        },
        dispatcher = Dispatchers.Unconfined,
    )

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L

        val VIEWPORT = BoundingBox(south = 51.0, west = 6.5, north = 51.4, east = 7.0)
    }
}
