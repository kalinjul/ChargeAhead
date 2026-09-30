package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.DestinationHistory
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.planWithSettings

/**
 * Plans [Params.from] → [Params.destination] with the selected vehicle and
 * makes it the planned trip and the app-wide destination. A failed plan
 * still sets the destination.
 */
class PlanTripInteractor(
    private val planner: TripPlanning,
    private val vehicles: VehicleRepository,
    private val preferences: PreferencesRepository,
    private val history: DestinationHistory,
    private val trips: TripRepository,
    private val dispatchers: AppCoroutineDispatchers,
) : Interactor<PlanTripInteractor.Params, TripPlanResult>() {

    /**
     * [startSocPercent] plans with a charge level that isn't persisted;
     * `null` takes the stored one.
     */
    data class Params(
        val from: LatLon,
        val destination: Destination,
        val startSocPercent: Double? = null,
    )

    override suspend fun doWork(params: Params): TripPlanResult {
        val result = planner.planWithSettings(vehicles, preferences, params.from, params.destination, params.startSocPercent, dispatchers.computation)
        trips.update { it.planned(params.destination, (result as? TripPlanResult.Planned)?.plan) }
        history.addRecentDestination(params.destination)
        return result
    }
}
