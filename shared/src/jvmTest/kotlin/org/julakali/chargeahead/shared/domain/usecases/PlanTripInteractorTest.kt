package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.DEFAULT_ASSUMED_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PlanTripTest {

    private val from = LatLon(49.45, 11.08)
    private val munich = Destination("München", LatLon(48.14, 11.58))
    private val vehicle = VehicleProfile("Testwagen", 77.0, 18.0, setOf(ConnectorType.CCS2))
    private val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
    private val trips = TripRepository()

    private data class Call(val from: LatLon, val startSocPercent: Double, val arrivalSocPercent: Double)

    private val calls = mutableListOf<Call>()
    private var outcome: (Destination) -> TripPlanResult = { destination -> TripPlanResult.Planned(plan(destination)) }

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
            calls += Call(from, startSocPercent, arrivalSocPercent)
            return outcome(destination)
        }
    }

    private val planTrip = PlanTripInteractor(planner, settings, trips)
    private val replanWithArrivalSoc =
        ReplanWithArrivalSocInteractor(UpdateArrivalSocInteractor(settings), trips, planTrip)
    private val commitTrip = CommitTripInteractor(trips) { 7L }
    private val replanCommitted =
        ReplanCommittedTripInteractor(planner, settings, trips, UpdateManualSocInteractor(settings)) { 9L }

    private fun plan(destination: Destination) = TripPlan(
        route = Route(listOf(from, destination.position), distanceKm = 170.0, durationMinutes = 100.0),
        destination = destination,
        stops = emptyList(),
        driveMinutes = 100.0,
        chargeMinutes = 0.0,
        arrivalSocPercent = 40.0,
    )

    @Test
    fun `without a vehicle nothing is planned`() = runBlocking {
        assertSame(TripPlanResult.NoVehicle, planTrip(PlanTripInteractor.Params(from, munich)).getOrThrow())
        assertNull(trips.state.value.planned)
    }

    @Test
    fun `a plan becomes the planned trip and its destination the app-wide one`() = runBlocking {
        settings.setVehicle(vehicle)

        val result = planTrip(PlanTripInteractor.Params(from, munich)).getOrThrow()

        assertEquals((result as TripPlanResult.Planned).plan, trips.state.value.planned)
        assertEquals(munich, trips.state.value.destination)
        assertEquals(listOf(munich), settings.recentDestinations.first())
    }

    @Test
    fun `a failed plan keeps the planned one but still sets the destination`() = runBlocking {
        settings.setVehicle(vehicle)
        planTrip(PlanTripInteractor.Params(from, munich)).getOrThrow()
        outcome = { TripPlanResult.NoRoute }
        val hamburg = Destination("Hamburg", LatLon(53.55, 9.99))

        planTrip(PlanTripInteractor.Params(from, hamburg)).getOrThrow()

        assertEquals(munich, trips.state.value.planned?.destination)
        assertEquals(hamburg, trips.state.value.destination)
    }

    @Test
    fun `the start charge is the given one - else the stored one - else the assumption`() = runBlocking {
        settings.setVehicle(vehicle)

        planTrip(PlanTripInteractor.Params(from, munich))
        settings.setManualSocPercent(55.0)
        planTrip(PlanTripInteractor.Params(from, munich))
        planTrip(PlanTripInteractor.Params(from, munich, startSocPercent = 30.0))

        assertEquals(listOf(DEFAULT_ASSUMED_SOC_PERCENT, 55.0, 30.0), calls.map { it.startSocPercent })
        assertEquals(55.0, settings.manualSocPercent.first(), "an explicit start charge is not persisted")
    }

    @Test
    fun `a new arrival charge re-plans the stored trip from where the driver is now`() = runBlocking {
        settings.setVehicle(vehicle)
        planTrip(PlanTripInteractor.Params(from, munich))
        val now = LatLon(49.0, 11.3)

        val result = replanWithArrivalSoc(ReplanWithArrivalSocInteractor.Params(25.0, now)).getOrThrow()

        assertIs<TripPlanResult.Planned>(result)
        assertEquals(Call(now, DEFAULT_ASSUMED_SOC_PERCENT, 25.0), calls.last())
        assertEquals(25.0, settings.arrivalSocPercent.first())
    }

    @Test
    fun `without a trip a new arrival charge is only stored`() = runBlocking {
        val result = replanWithArrivalSoc(ReplanWithArrivalSocInteractor.Params(25.0, from)).getOrThrow()

        assertNull(result)
        assertEquals(25.0, settings.arrivalSocPercent.first())
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `committing replaces the committed trip and clears the planned one`() = runBlocking {
        settings.setVehicle(vehicle)
        val planned = (planTrip(PlanTripInteractor.Params(from, munich)).getOrThrow() as TripPlanResult.Planned).plan

        val trip = commitTrip(CommitTripInteractor.Params(planned, 60.0)).getOrThrow()

        assertEquals(CommittedTrip(planned, 60.0, 7L), trip)
        assertEquals(trip, trips.state.value.committed)
        assertNull(trips.state.value.planned)
    }

    @Test
    fun `re-planning the committed trip commits the new plan and leaves the planned one alone`() = runBlocking {
        settings.setVehicle(vehicle)
        val first = (planTrip(PlanTripInteractor.Params(from, munich)).getOrThrow() as TripPlanResult.Planned).plan
        commitTrip(CommitTripInteractor.Params(first, 60.0)).getOrThrow()
        val kiel = Destination("Kiel", LatLon(54.32, 10.14))
        planTrip(PlanTripInteractor.Params(from, kiel)).getOrThrow()
        val now = LatLon(49.0, 11.3)

        val result = replanCommitted(ReplanCommittedTripInteractor.Params(now, socPercent = 45.0)).getOrThrow()

        assertIs<TripPlanResult.Planned>(result)
        assertEquals(Call(now, 45.0, settings.arrivalSocPercent.first()), calls.last())
        assertEquals(munich, trips.state.value.committed?.plan?.destination)
        assertEquals(45.0, trips.state.value.committed?.startSocPercent)
        assertEquals(45.0, settings.manualSocPercent.first())
        assertEquals(kiel, trips.state.value.planned?.destination)
    }

    @Test
    fun `a failed re-plan keeps the committed trip`() = runBlocking {
        settings.setVehicle(vehicle)
        val first = (planTrip(PlanTripInteractor.Params(from, munich)).getOrThrow() as TripPlanResult.Planned).plan
        val committed = commitTrip(CommitTripInteractor.Params(first, 60.0)).getOrThrow()
        outcome = { TripPlanResult.NoRoute }

        val result = replanCommitted(ReplanCommittedTripInteractor.Params(from)).getOrThrow()

        assertSame(TripPlanResult.NoRoute, result)
        assertEquals(committed, trips.state.value.committed)
    }

    @Test
    fun `without a committed trip nothing is re-planned`() = runBlocking {
        settings.setVehicle(vehicle)

        assertNull(replanCommitted(ReplanCommittedTripInteractor.Params(from)).getOrThrow())
        assertEquals(emptyList(), calls)
    }
}
