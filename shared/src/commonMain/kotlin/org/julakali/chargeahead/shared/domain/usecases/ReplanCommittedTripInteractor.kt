package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.planWithSettings
import kotlinx.coroutines.flow.first

/**
 * Plans the committed trip's destination anew from [Params.from] and commits
 * the result in its place. The planned trip on the map is left alone.
 * `null` when there is no committed trip.
 */
class ReplanCommittedTripInteractor(
    private val planner: TripPlanning,
    private val vehicles: VehicleRepository,
    private val preferences: PreferencesRepository,
    private val trips: TripRepository,
    private val updateManualSoc: UpdateManualSocInteractor,
    private val time: TimeProvider,
    private val dispatchers: AppCoroutineDispatchers,
) : Interactor<ReplanCommittedTripInteractor.Params, TripPlanResult?>() {

    /**
     * A [socPercent] is stored as the manual level unless [storeSoc] is false;
     * `null` takes the stored one.
     */
    data class Params(val from: LatLon, val socPercent: Double? = null, val storeSoc: Boolean = true)

    override suspend fun doWork(params: Params): TripPlanResult? {
        val destination = trips.state.value.committed?.plan?.destination ?: return null
        params.socPercent?.takeIf { params.storeSoc }?.let { updateManualSoc(UpdateManualSocInteractor.Params(it)).getOrThrow() }
        val result = planner.planWithSettings(vehicles, preferences, params.from, destination, params.socPercent, dispatchers.computation)
        if (result is TripPlanResult.Planned) {
            val plannedWith = params.socPercent ?: vehicles.manualSocPercent.first()
            val trip = CommittedTrip(result.plan, plannedWith, time.nowMillis())
            trips.update { it.replanned(trip) }
        }
        return result
    }
}
