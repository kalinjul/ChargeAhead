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
import org.julakali.chargeahead.shared.domain.TripStore
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.routeId
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
    private val store = TripStore()

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

    private val planTrip = PlanTripInteractor(planner, settings, store)
    private val replanWithArrivalSoc =
        ReplanWithArrivalSocInteractor(UpdateArrivalSocInteractor(settings), store, planTrip)

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
        assertNull(store.plan.value)
    }

    @Test
    fun `a plan lands in the store and its destination becomes the app-wide one`() = runBlocking {
        settings.setVehicle(vehicle)

        val result = planTrip(PlanTripInteractor.Params(from, munich)).getOrThrow()

        assertEquals((result as TripPlanResult.Planned).plan, store.plan.value)
        assertEquals(munich, settings.destination.first())
    }

    @Test
    fun `a failed plan keeps the stored one`() = runBlocking {
        settings.setVehicle(vehicle)
        planTrip(PlanTripInteractor.Params(from, munich)).getOrThrow()
        outcome = { TripPlanResult.NoRoute }

        planTrip(PlanTripInteractor.Params(from, Destination("Hamburg", LatLon(53.55, 9.99)))).getOrThrow()

        assertEquals(munich, store.plan.value?.destination)
    }

    @Test
    fun `the start charge is the given one - else the stored one - else the assumption`() = runBlocking {
        settings.setVehicle(vehicle)

        planTrip(PlanTripInteractor.Params(from, munich))
        settings.setManualSocPercent(55.0)
        planTrip(PlanTripInteractor.Params(from, munich))
        planTrip(PlanTripInteractor.Params(from, munich, startSocPercent = 30.0))

        assertEquals(listOf(PlanTripInteractor.DEFAULT_ASSUMED_SOC_PERCENT, 55.0, 30.0), calls.map { it.startSocPercent })
        assertEquals(55.0, settings.manualSocPercent.first(), "an explicit start charge is not persisted")
    }

    @Test
    fun `a new arrival charge re-plans the stored trip from where the driver is now`() = runBlocking {
        settings.setVehicle(vehicle)
        planTrip(PlanTripInteractor.Params(from, munich))
        val now = LatLon(49.0, 11.3)

        val result = replanWithArrivalSoc(ReplanWithArrivalSocInteractor.Params(25.0, now)).getOrThrow()

        assertIs<TripPlanResult.Planned>(result)
        assertEquals(Call(now, PlanTripInteractor.DEFAULT_ASSUMED_SOC_PERCENT, 25.0), calls.last())
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
    fun `toggling a route saves it, and a second toggle removes it`() = runBlocking {
        val toggle = ToggleSavedRouteInteractor(settings)

        assertEquals(true, toggle(ToggleSavedRouteInteractor.Params(munich, "170 km")).getOrThrow())
        assertEquals(listOf(munich.routeId()), settings.savedRoutes.first().map { it.id })

        assertEquals(false, toggle(ToggleSavedRouteInteractor.Params(munich, "170 km")).getOrThrow())
        assertEquals(emptyList(), settings.savedRoutes.first())
    }
}
