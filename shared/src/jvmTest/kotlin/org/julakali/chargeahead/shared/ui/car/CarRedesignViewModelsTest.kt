package org.julakali.chargeahead.shared.ui.car

import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.data.DataStoreTripStorage
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargePointState
import org.julakali.chargeahead.shared.domain.ChargePointStatus
import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.usecases.CommitTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.LiveConnectorsObserver
import org.julakali.chargeahead.shared.domain.usecases.PlanTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.ReplanCommittedTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
import org.julakali.chargeahead.shared.settings.DataStoreDestinationHistory
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CarRedesignViewModelsTest {

    @BeforeTest
    fun setUpMainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun tearDownMainDispatcher() = Dispatchers.resetMain()

    private val settings = InMemoryPreferencesDataStore()
    private val vehicles = DataStoreVehicleRepository(settings)
    private val trips = TripRepository(DataStoreTripStorage(InMemoryPreferencesDataStore()))
    private val time = TimeProvider { 1_700_000_000_000L }
    private val dispatchers = AppCoroutineDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined)
    private val plannedWith = mutableListOf<Double>()

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T = withTimeout(5_000) { first(matching) }

    @Test
    fun `the home tiles show the charge level the feature knows`() = runBlocking<Unit> {
        val energy = MutableStateFlow<EnergyState?>(null)
        val feature = feature(energy, SoCSourceKind.MANUAL)
        val viewModel = CarHomeViewModel(feature, trips)
        assertNull(viewModel.uiState.await { true }.socPercent)

        energy.value = EnergyState(63.4, SoCSourceKind.MANUAL, 0L)

        assertEquals(63, viewModel.uiState.await { it.socPercent != null }.socPercent)
    }

    @Test
    fun `neu planen plans right away only when the car reports its charge`() = runBlocking<Unit> {
        withVehicleAndCommittedTrip()
        val manual = routeViewModel(feature(MutableStateFlow(EnergyState(50.0, SoCSourceKind.MANUAL, 0L)), SoCSourceKind.MANUAL))
        manual.uiState.await { it is CarRouteUiState.Ready }

        assertFalse(manual.onReplanRequested())
        assertTrue(plannedWith.isEmpty())

        val car = routeViewModel(feature(MutableStateFlow(EnergyState(42.0, SoCSourceKind.CAR_HARDWARE, 0L)), SoCSourceKind.CAR_HARDWARE))
        car.uiState.await { it is CarRouteUiState.Ready }

        assertTrue(car.onReplanRequested())
        assertEquals(listOf(42.0), plannedWith)
    }

    @Test
    fun `a picked level re-plans with it and keeps it as the manual one`() = runBlocking<Unit> {
        withVehicleAndCommittedTrip()
        val viewModel = routeViewModel(feature(MutableStateFlow(null), SoCSourceKind.MANUAL))
        viewModel.uiState.await { it is CarRouteUiState.Ready }

        viewModel.onReplanWith(30)

        assertEquals(listOf(30.0), plannedWith)
        assertEquals(30.0, vehicles.manualSocPercent.first())
    }

    @Test
    fun `the detail measures from the fix and shows the live points`() = runBlocking<Unit> {
        val statuses = MutableStateFlow(mapOf("live:1" to listOf(ChargePointStatus(ChargePointState.AVAILABLE, 150.0, listOf(ConnectorType.CCS2)))))
        val repository = object : ChargePointStatusRepository {
            override val statuses: Flow<Map<String, List<ChargePointStatus>>> = statuses
            override suspend fun refresh(ids: Collection<String>) = Unit
        }
        val feature = feature(MutableStateFlow(null), SoCSourceKind.MANUAL)
        val viewModel = CarSiteDetailViewModel(feature, site.copy(liveStatusId = "live:1"), LiveConnectorsObserver(repository))

        val state = viewModel.uiState.await { it.distanceKm != null && it.live != null }

        assertTrue(state.distanceKm!! in 100.0..120.0, "was ${state.distanceKm}")
        assertEquals(1, state.live!!.single().available)
    }

    private fun feature(energy: Flow<EnergyState?>, kind: SoCSourceKind): ChargeStopsFeature =
        ChargeStopsFeature(
            locationSource = object : LocationSource {
                override val updates: Flow<Fix> = flowOf(Fix(hamburg, null, null, 0L))
                override suspend fun currentFix(): Fix = Fix(hamburg, null, null, 0L)
            },
            socSource = object : SoCSource {
                override val kind: SoCSourceKind = kind
                override val energy: Flow<EnergyState?> = energy
            },
            parentScope = CoroutineScope(Dispatchers.Unconfined),
        ).apply { start() }

    private fun withVehicleAndCommittedTrip() = runBlocking {
        vehicles.setVehicle(VehicleProfile("Test-EV", 75.0, 18.0, setOf(ConnectorType.CCS2)))
        CommitTripInteractor(trips, time)(CommitTripInteractor.Params(plan, startSocPercent = 60.0)).getOrThrow()
    }

    private fun routeViewModel(feature: ChargeStopsFeature): CarRouteViewModel {
        val preferences = DataStorePreferencesRepository(settings)
        val planner = object : TripPlanning {
            override suspend fun plan(
                from: LatLon,
                destination: Destination,
                vehicle: VehicleProfile,
                startSocPercent: Double,
                arrivalSocPercent: Double,
                filters: ChargeFilters,
                networks: NetworkPreferences,
            ): TripPlanResult {
                plannedWith += startSocPercent
                return TripPlanResult.Planned(plan)
            }
        }
        return CarRouteViewModel(
            feature = feature,
            destination = plan.destination,
            activeRoute = true,
            planTrip = PlanTripInteractor(planner, vehicles, preferences, DataStoreDestinationHistory(settings), trips, dispatchers),
            replanCommittedTrip = ReplanCommittedTripInteractor(planner, vehicles, preferences, trips, UpdateManualSocInteractor(vehicles), time, dispatchers),
            trips = trips,
        )
    }

    private companion object {
        val hamburg = LatLon(53.55, 9.99)
        val site = ChargeSite("test:1", "Lader", "Operator", position = LatLon(54.5, 10.1), connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 2)))
        val munich = Destination("München", LatLon(48.137, 11.575))
        val plan = TripPlan(
            route = Route(listOf(hamburg, munich.position), distanceKm = 776.0, durationMinutes = 470.0),
            destination = munich,
            stops = emptyList(),
            driveMinutes = 470.0,
            chargeMinutes = 0.0,
            arrivalSocPercent = 22.0,
        )
    }
}
