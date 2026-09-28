package org.julakali.chargeahead.shared.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripStore
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.usecases.CommitTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.EndTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.PlanTripInteractor
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommittedTripViewModelTest {

    @BeforeTest
    fun setUpMainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun tearDownMainDispatcher() = Dispatchers.resetMain()

    private val hamburg = LatLon(53.55, 9.99)
    private val hannover = LatLon(52.37, 9.73)
    private val muenchen = Destination("München", LatLon(48.137, 11.575))
    private val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
    private val store = TripStore()
    private var plannedFrom: LatLon? = null

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
            return TripPlanResult.Planned(plan(from))
        }
    }

    private val feature = ChargeStopsFeature(
        locationSource = object : LocationSource {
            override val updates: Flow<Fix> = MutableSharedFlow()
            override suspend fun currentFix() = Fix(hannover, null, null, 0L)
        },
        dispatcher = Dispatchers.Unconfined,
    )

    private fun viewModel() = CommittedTripViewModel(
        settings = settings,
        feature = feature,
        planTrip = PlanTripInteractor(planner, settings, store),
        commitTrip = CommitTripInteractor(settings) { 42L },
        endTrip = EndTripInteractor(settings),
        tripStore = store,
    )

    private fun commitFromHamburg() = runBlocking {
        settings.setVehicle(VehicleProfile("Testwagen", 77.0, 18.0, setOf(ConnectorType.CCS2)))
        settings.commitTrip(CommittedTrip(plan(hamburg), startSocPercent = 60.0, committedAtEpochMillis = 1L))
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

        assertEquals(CommittedTripEvent.Replanned, event)
        assertEquals(hannover, plannedFrom)
        val stored = settings.committedTrip.first()!!
        assertEquals(plan(hannover), stored.plan)
        assertEquals(60.0, stored.startSocPercent)
        assertEquals(42L, stored.committedAtEpochMillis)
        assertNull(store.plan.value, "the re-plan is committed, not left on the home screen")
    }

    @Test
    fun `ending the trip clears it`() = runBlocking {
        commitFromHamburg()
        val viewModel = viewModel()
        viewModel.uiState.first { it.trip != null }

        viewModel.endTrip()

        assertNull(settings.committedTrip.first())
        assertEquals(CommittedTripEvent.Ended, viewModel.event.value)
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
}
