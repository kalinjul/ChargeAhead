package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.FakeVehicleCatalog
import org.julakali.chargeahead.shared.testDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.ChargeFilters
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
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.UnreachableTrip
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.usecases.CommitTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.DismissPlannedTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.PlanTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.ReplanWithArrivalSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateArrivalSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.DataStoreDestinationHistory
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripViewModelTest {

    private val main = TestMain()

    @BeforeTest
    fun setUpMainDispatcher() = main.setUp()

    @AfterTest
    fun tearDownMainDispatcher() = main.tearDown()

    private val hamburg = LatLon(53.55, 9.99)
    private val muenchen = Destination("München", LatLon(48.137, 11.575))
    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val history = DataStoreDestinationHistory(InMemoryPreferencesDataStore())
    private val trips = TripRepository()
    private var plannedSoc: Double? = null
    private var plans = 0
    private var outcome: () -> TripPlanResult = { TripPlanResult.Planned(plan()) }

    private fun plan() = TripPlan(
        route = Route(listOf(hamburg, muenchen.position), distanceKm = 776.0, durationMinutes = 470.0),
        destination = muenchen,
        stops = emptyList(),
        driveMinutes = 470.0,
        chargeMinutes = 0.0,
        arrivalSocPercent = 30.0,
    )

    private val planner = object : TripPlanning {
        override suspend fun plan(
            from: LatLon,
            destination: Destination,
            vehicle: VehicleProfile,
            startSocPercent: Double,
            arrivalSocPercent: Double,
            filters: ChargeFilters,
            networks: NetworkPreferences,
        ): TripPlanResult {
            plans++
            plannedSoc = startSocPercent
            return outcome()
        }
    }

    private fun feature(carSoc: Double?) = ChargeStopsFeature(
        locationSource = object : LocationSource {
            override val updates: Flow<Fix> = MutableSharedFlow()
            override suspend fun currentFix() = Fix(hamburg, null, null, 0L)
        },
        socSource = carSoc?.let { soc ->
            object : SoCSource {
                override val kind = SoCSourceKind.CAR_HARDWARE
                override val energy: Flow<EnergyState?> = flowOf(EnergyState(soc, SoCSourceKind.CAR_HARDWARE, 0L))
            }
        },
        parentScope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun viewModel(feature: ChargeStopsFeature): TripViewModel {
        val planTrip = PlanTripInteractor(planner, vehicles, FakeVehicleCatalog(), preferences, history, trips, testDispatchers)
        return main.track(
            TripViewModel(
                feature = feature,
                planTrip = planTrip,
                replanWithArrivalSoc = ReplanWithArrivalSocInteractor(UpdateArrivalSocInteractor(vehicles), trips, planTrip),
                commitTrip = CommitTripInteractor(trips) { 0L },
                updateManualSoc = UpdateManualSocInteractor(vehicles),
                dismissPlannedTrip = DismissPlannedTripInteractor(trips),
                trips = trips,
                vehicles = vehicles,
            ),
        )
    }

    private suspend fun planned(feature: ChargeStopsFeature): TripViewModel {
        vehicles.setVehicle(VehicleProfile("Testwagen", 77.0, 18.0, setOf(ConnectorType.CCS2)))
        vehicles.setManualSocPercent(55.0)
        feature.start()
        feature.locate()
        val viewModel = viewModel(feature)
        viewModel.plan(muenchen)
        withTimeout(5_000) { viewModel.uiState.first { it is TripUiState.Planned } }
        return viewModel
    }

    @Test
    fun `neu planen without a car reading opens the charge-level prompt, seeded with the stored level`() = runBlocking {
        val viewModel = planned(feature(carSoc = null))
        val before = plans

        viewModel.onReplanRequested()

        val state = withTimeout(5_000) { viewModel.uiState.first { (it as? TripUiState.Planned)?.socInput != null } }
        assertEquals("55", (state as TripUiState.Planned).socInput)
        assertEquals(before, plans, "nothing planned until the level is confirmed")
    }

    @Test
    fun `neu planen with a car reading plans straight away`() = runBlocking {
        val feature = feature(carSoc = 42.0)
        val viewModel = planned(feature)
        withTimeout(5_000) { feature.currentEnergy.first { it != null } }
        val before = plans

        viewModel.onReplanRequested()

        val state = withTimeout(5_000) { viewModel.uiState.first { it is TripUiState.Planned && plans == before + 1 } }
        assertEquals(TripEvent.SheetReady, viewModel.event.value)
        assertNull((state as TripUiState.Planned).socInput)
    }

    @Test
    fun `no charger in reach keeps the sheet on the destination, start level still editable`() = runBlocking {
        outcome = { TripPlanResult.NoChargerInReach(afterKm = 0.0) }
        val feature = feature(carSoc = null)
        vehicles.setVehicle(VehicleProfile("Testwagen", 77.0, 18.0, setOf(ConnectorType.CCS2)))
        vehicles.setManualSocPercent(5.0)
        feature.start()
        feature.locate()
        val viewModel = viewModel(feature)

        viewModel.plan(muenchen)

        val state = withTimeout(5_000) { viewModel.uiState.first { it is TripUiState.Unreachable } } as TripUiState.Unreachable
        assertEquals(muenchen, state.destination)
        assertEquals(UnreachableTrip.NoCharger(afterKm = 0.0), state.why)
        assertEquals(5.0, state.startSocPercent)

        viewModel.onStartSocEditRequested()
        withTimeout(5_000) { viewModel.uiState.first { (it as? TripUiState.Unreachable)?.socInput == "5" } }
        outcome = { TripPlanResult.Planned(plan()) }
        viewModel.onStartSocInputChanged("60")
        viewModel.onStartSocConfirmed()

        withTimeout(5_000) { viewModel.uiState.first { it is TripUiState.Planned } }
        assertEquals(60.0, plannedSoc)
    }

    @Test
    fun `closing an unreachable trip goes back to browsing`() = runBlocking {
        outcome = { TripPlanResult.NoChargerInReach(afterKm = 0.0) }
        val feature = feature(carSoc = null)
        vehicles.setVehicle(VehicleProfile("Testwagen", 77.0, 18.0, setOf(ConnectorType.CCS2)))
        feature.start()
        feature.locate()
        val viewModel = viewModel(feature)
        viewModel.plan(muenchen)
        withTimeout(5_000) { viewModel.uiState.first { it is TripUiState.Unreachable } }

        viewModel.clear()

        withTimeout(5_000) { viewModel.uiState.first { it == TripUiState.NoPlan } }
        assertNull(trips.state.value.unreachable)
    }
}
