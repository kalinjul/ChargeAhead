package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.FakeVehicleCatalog
import org.julakali.chargeahead.shared.testDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.EnergyState
import kotlinx.coroutines.flow.flowOf
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.SectionSelection
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.UnreachableTrip
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.usecases.EndTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.ReplanCommittedTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import org.julakali.chargeahead.shared.testVehicleRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActiveRouteViewModelTest {

    private val main = TestMain()

    @BeforeTest
    fun setUpMainDispatcher() = main.setUp()

    @AfterTest
    fun tearDownMainDispatcher() = main.tearDown()

    private val hamburg = LatLon(53.55, 9.99)
    private val hannover = LatLon(52.37, 9.73)
    private val muenchen = Destination("München", LatLon(48.137, 11.575))
    private val vehicles = testVehicleRepository()
    private val trips = TripRepository()
    private var plannedFrom: LatLon? = null
    private var plannedSoc: Double? = null
    private var outcome: ((LatLon) -> TripPlanResult)? = null

    private fun plan(from: LatLon) = TripPlan(
        route = Route(listOf(from, muenchen.position), distanceKm = 776.0, durationMinutes = 470.0),
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
            plannedFrom = from
            plannedSoc = startSocPercent
            return outcome?.invoke(from) ?: TripPlanResult.Planned(plan(from))
        }
    }

    private fun feature(carSoc: Double? = null) = ChargeStopsFeature(
        locationSource = object : LocationSource {
            override val updates: Flow<Fix> = MutableSharedFlow()
            override suspend fun currentFix() = Fix(hannover, null, null, 0L)
        },
        socSource = carSoc?.let { soc ->
            object : SoCSource {
                override val kind = SoCSourceKind.CAR_HARDWARE
                override val energy: Flow<EnergyState?> = flowOf(EnergyState(soc, SoCSourceKind.CAR_HARDWARE, 0L))
            }
        },
        parentScope = CoroutineScope(Dispatchers.Unconfined),
    )

    private val feature = feature()

    private fun viewModel(feature: ChargeStopsFeature = this.feature) = main.track(ActiveRouteViewModel(
        vehicles = vehicles,
        feature = feature,
        replanCommittedTrip = ReplanCommittedTripInteractor(
            planner,
            vehicles,
            FakeVehicleCatalog(),
            DataStorePreferencesRepository(InMemoryPreferencesDataStore()),
            trips,
            UpdateManualSocInteractor(vehicles),
            { 42L },
            testDispatchers,
        ),
        endTrip = EndTripInteractor(trips),
        trips = trips,
    ))

    private fun commitFromHamburg() = runBlocking {
        vehicles.setVehicle(VehicleProfile("Testwagen", 77.0, 18.0, setOf(ConnectorType.CCS2)))
        // The level the trip was sent with, and still the stored one when it is planned again.
        vehicles.setManualSocPercent(60.0)
        trips.update { it.committed(CommittedTrip(plan(hamburg), startSocPercent = 60.0, committedAtEpochMillis = 1L)) }
    }

    /** The page is its own ViewModel scope: an empty first value would slide in an empty page. */
    @Test
    fun `the stored trip is there from the first value`() {
        commitFromHamburg()
        assertEquals(plan(hamburg), viewModel().uiState.value.trip?.plan)
    }

    @Test
    fun `the stored trip is what the view shows`() = runBlocking {
        commitFromHamburg()
        val viewModel = viewModel()
        assertEquals(plan(hamburg), viewModel.uiState.first { it.trip != null }.trip?.plan)
    }

    @Test
    fun `replan plans from the current fix and stores that plan`() = runBlocking {
        commitFromHamburg()
        feature.locate()
        val viewModel = viewModel()
        viewModel.uiState.first { it.trip != null }

        viewModel.replan()
        // Planning hops to Dispatchers.Default, so the outcome arrives a little later.
        val event = withTimeout(5_000) { viewModel.event.first { it != null } }

        assertEquals(ActiveRouteEvent.Replanned, event)
        assertEquals(hannover, plannedFrom)
        val stored = trips.state.value.committed!!
        assertEquals(plan(hannover), stored.plan)
        assertEquals(60.0, stored.startSocPercent)
        assertEquals(42L, stored.committedAtEpochMillis)
        assertNull(trips.state.value.planned, "the re-plan is committed, not left on the home screen")
    }

    @Test
    fun `a failed replan says why and keeps the trip`() = runBlocking {
        commitFromHamburg()
        feature.locate()
        val viewModel = viewModel()
        viewModel.uiState.first { it.trip != null }

        outcome = { TripPlanResult.NoChargerInReach(afterKm = 0.0) }
        viewModel.replan(socPercent = 5.0)
        assertEquals(ActiveRouteEvent.ReplanFailed(UnreachableTrip.NoCharger(afterKm = 0.0)), withTimeout(5_000) { viewModel.event.first { it != null } })
        viewModel.onEventHandled()

        outcome = { TripPlanResult.NoRoute }
        viewModel.replan()
        assertEquals(ActiveRouteEvent.ReplanFailed(UnreachableTrip.NoRoute), withTimeout(5_000) { viewModel.event.first { it != null } })
        viewModel.onEventHandled()

        outcome = { TripPlanResult.NoConnection }
        viewModel.replan()
        assertEquals(ActiveRouteEvent.ReplanFailed(UnreachableTrip.NoConnection), withTimeout(5_000) { viewModel.event.first { it != null } })
        viewModel.onEventHandled()

        vehicles.setVehicle(null)
        viewModel.replan()
        assertEquals(ActiveRouteEvent.VehicleMissing, withTimeout(5_000) { viewModel.event.first { it != null } })

        assertEquals(plan(hamburg), trips.state.value.committed?.plan)
    }

    @Test
    fun `ending the trip clears it`() = runBlocking {
        commitFromHamburg()
        val viewModel = viewModel()
        viewModel.uiState.first { it.trip != null }

        viewModel.endTrip()

        assertNull(trips.state.value.committed)
        assertEquals(ActiveRouteEvent.Ended, viewModel.event.value)
    }

    @Test
    fun `the maps url follows the section selection`() = runBlocking {
        commitFromHamburg()
        val viewModel = viewModel()
        viewModel.uiState.first { it.trip != null }

        viewModel.onSectionSelectingToggled()
        viewModel.onSectionPointPicked(0)
        viewModel.onSectionPointPicked(1)

        assertTrue(viewModel.uiState.value.mapsUrl!!.contains("48.137"))
        viewModel.onSectionSent()
        assertEquals(SectionSelection(), viewModel.uiState.value.selection)
    }

    @Test
    fun `neu planen without a car reading asks for the level, seeded with the stored one`() = runBlocking {
        commitFromHamburg()
        vehicles.setManualSocPercent(47.0)
        feature.locate()
        val viewModel = viewModel()
        viewModel.uiState.first { it.trip != null }

        viewModel.onReplanRequested()

        assertEquals("47", withTimeout(5_000) { viewModel.uiState.first { it.socInput != null } }.socInput)
        assertNull(plannedFrom, "nothing planned before the level is confirmed")
    }

    @Test
    fun `the confirmed level drives the re-plan and is kept`() = runBlocking {
        commitFromHamburg()
        feature.locate()
        val viewModel = viewModel()
        viewModel.uiState.first { it.trip != null }
        viewModel.onReplanRequested()
        withTimeout(5_000) { viewModel.uiState.first { it.socInput != null } }

        viewModel.onSocInputChanged("3")
        viewModel.onSocInputChanged("35")
        viewModel.onSocConfirmed()
        withTimeout(5_000) { viewModel.event.first { it != null } }

        assertEquals(35.0, plannedSoc)
        assertEquals(35.0, vehicles.manualSocPercent.first())
        assertEquals(35.0, trips.state.value.committed!!.startSocPercent)
        assertNull(viewModel.uiState.value.socInput)
    }

    @Test
    fun `dismissing the prompt plans nothing`() = runBlocking {
        commitFromHamburg()
        feature.locate()
        val viewModel = viewModel()
        viewModel.uiState.first { it.trip != null }
        viewModel.onReplanRequested()
        withTimeout(5_000) { viewModel.uiState.first { it.socInput != null } }

        viewModel.onSocEditDismissed()

        assertNull(viewModel.uiState.value.socInput)
        assertNull(plannedFrom)
    }

    @Test
    fun `neu planen with a car reading plans at once and stores the level the car gave`() = runBlocking {
        commitFromHamburg()
        val carFeature = feature(carSoc = 42.0)
        carFeature.start()
        carFeature.locate()
        withTimeout(5_000) { carFeature.currentEnergy.first { it != null } }
        // The car's reading lands in the stored level, which is what the planner reads.
        vehicles.setManualSocPercent(42.0)
        val viewModel = viewModel(carFeature)
        viewModel.uiState.first { it.trip != null }

        viewModel.onReplanRequested()
        withTimeout(5_000) { viewModel.event.first { it != null } }

        assertNull(viewModel.uiState.value.socInput)
        assertEquals(42.0, trips.state.value.committed!!.startSocPercent)
    }

    @Test
    fun `without a position there is nothing to re-plan from`() = runBlocking {
        commitFromHamburg()
        val viewModel = viewModel()
        val state = viewModel.uiState.first { it.trip != null }

        assertEquals(false, state.canReplan)
        viewModel.replan()
        assertNull(viewModel.event.value)
        assertEquals(plan(hamburg), trips.state.value.committed!!.plan)
    }
}
