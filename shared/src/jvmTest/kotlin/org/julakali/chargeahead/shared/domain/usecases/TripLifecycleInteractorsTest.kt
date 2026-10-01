package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.data.DataStoreTripStorage
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.TripState
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The trip's life on the real repository over the real storage, one in-memory file per test. */
class TripLifecycleInteractorsTest {

    private val tripFile = InMemoryPreferencesDataStore()
    private val trips = TripRepository(DataStoreTripStorage(tripFile))
    private val time = TimeProvider { NOW }

    private val commit = CommitTripInteractor(trips, time)
    private val end = EndTripInteractor(trips)
    private val dismiss = DismissPlannedTripInteractor(trips)

    /** What a fresh process would read back from the same file. */
    private suspend fun afterRestart(): TripState? = DataStoreTripStorage(tripFile).read()

    @Test
    fun `committing with nothing planned still becomes the trip`() = runBlocking {
        val trip = commit(CommitTripInteractor.Params(munich, startSocPercent = 55.0)).getOrThrow()

        assertEquals(CommittedTrip(munich, 55.0, NOW), trip)
        assertEquals(TripState(munich.destination, planned = null, committed = trip), trips.state.value)
    }

    @Test
    fun `committing stores the plan and takes the planned trip off the map`() = runBlocking {
        trips.update { it.planned(munich.destination, munich) }

        val trip = commit(CommitTripInteractor.Params(munich, startSocPercent = null)).getOrThrow()

        assertNull(trips.state.value.planned)
        assertEquals(trip, trips.state.value.committed)
        assertEquals(trips.state.value, afterRestart())
    }

    @Test
    fun `ending clears the committed trip and nothing else`() = runBlocking {
        commit(CommitTripInteractor.Params(munich, 60.0)).getOrThrow()
        trips.update { it.planned(kiel.destination, kiel) }

        end(Unit).getOrThrow()

        assertEquals(TripState(kiel.destination, planned = kiel, committed = null), trips.state.value)
        assertEquals(trips.state.value, afterRestart())
    }

    @Test
    fun `ending with no trip is a no-op`() = runBlocking {
        end(Unit).getOrThrow()

        assertEquals(TripState(), trips.state.value)
    }

    @Test
    fun `dismissing the plan leaves the committed trip and the destination alone`() = runBlocking {
        val trip = commit(CommitTripInteractor.Params(munich, 60.0)).getOrThrow()
        trips.update { it.planned(kiel.destination, kiel) }

        dismiss(Unit).getOrThrow()

        assertEquals(TripState(kiel.destination, planned = null, committed = trip), trips.state.value)
        assertEquals(trips.state.value, afterRestart())
    }

    @Test
    fun `the committed trip comes back in a second repository over the same file`() = runBlocking {
        val trip = commit(CommitTripInteractor.Params(munich, 60.0)).getOrThrow()

        val restarted = TripRepository(DataStoreTripStorage(tripFile))
        restarted.restore()

        assertEquals(trip, restarted.state.value.committed)
        assertNull(restarted.state.value.planned)
    }

    @Test
    fun `replanning without a committed trip does nothing`() = runBlocking {
        val replan = replanInteractor(planner = FixedPlanner(TripPlanResult.Planned(kiel)))

        assertNull(replan(ReplanCommittedTripInteractor.Params(from = LatLon(52.52, 13.40))).getOrThrow())
        assertEquals(TripState(), trips.state.value)
    }

    @Test
    fun `replanning replaces the committed trip and leaves the planned one on the map`() = runBlocking {
        commit(CommitTripInteractor.Params(munich, 60.0)).getOrThrow()
        trips.update { it.planned(kiel.destination, kiel) }
        val rerouted = munich.copy(driveMinutes = 333.0)
        val replan = replanInteractor(planner = FixedPlanner(TripPlanResult.Planned(rerouted)))

        val result = replan(ReplanCommittedTripInteractor.Params(from = LatLon(52.0, 12.0), socPercent = 42.0, storeSoc = false)).getOrThrow()

        assertEquals(TripPlanResult.Planned(rerouted), result)
        assertEquals(CommittedTrip(rerouted, 42.0, NOW), trips.state.value.committed)
        assertEquals(kiel, trips.state.value.planned)
        assertEquals(trips.state.value, afterRestart())
    }

    @Test
    fun `a failed replan keeps the committed trip`() = runBlocking {
        val trip = commit(CommitTripInteractor.Params(munich, 60.0)).getOrThrow()
        val replan = replanInteractor(planner = FixedPlanner(TripPlanResult.NoRoute))

        val result = replan(ReplanCommittedTripInteractor.Params(from = LatLon(52.0, 12.0))).getOrThrow()

        assertEquals(TripPlanResult.NoRoute, result)
        assertEquals(trip, trips.state.value.committed)
    }

    private fun replanInteractor(planner: TripPlanning): ReplanCommittedTripInteractor {
        val settings = InMemoryPreferencesDataStore()
        val vehicles = DataStoreVehicleRepository(settings)
        runBlocking {
            vehicles.setVehicle(VehicleProfile("Test-EV", 75.0, 18.0, setOf(ConnectorType.CCS2)))
            vehicles.setManualSocPercent(80.0)
        }
        return ReplanCommittedTripInteractor(
            planner = planner,
            vehicles = vehicles,
            preferences = DataStorePreferencesRepository(settings),
            trips = trips,
            updateManualSoc = UpdateManualSocInteractor(vehicles),
            time = time,
            dispatchers = AppCoroutineDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined),
        )
    }

    private class FixedPlanner(private val result: TripPlanResult) : TripPlanning {
        override suspend fun plan(
            from: LatLon,
            destination: Destination,
            vehicle: VehicleProfile,
            startSocPercent: Double,
            arrivalSocPercent: Double,
            filters: ChargeFilters,
            networks: NetworkPreferences,
        ): TripPlanResult = result
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        val munich = trip("München", LatLon(48.14, 11.58))
        val kiel = trip("Kiel", LatLon(54.32, 10.14))

        fun trip(name: String, position: LatLon) = TripPlan(
            route = Route(listOf(LatLon(52.52, 13.40), position), distanceKm = 500.0, durationMinutes = 300.0),
            destination = Destination(name, position),
            stops = emptyList(),
            driveMinutes = 300.0,
            chargeMinutes = 20.0,
            arrivalSocPercent = 30.0,
        )
    }
}
