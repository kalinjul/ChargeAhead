package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripRepository

/**
 * Stores a new arrival level and re-plans the planned trip with it. `null`
 * when there was no trip to re-plan.
 */
class ReplanWithArrivalSocInteractor(
    private val updateArrivalSoc: UpdateArrivalSocInteractor,
    private val trips: TripRepository,
    private val planTrip: PlanTripInteractor,
) : Interactor<ReplanWithArrivalSocInteractor.Params, TripPlanResult?>() {

    /** [from] is where the re-planned trip starts. */
    data class Params(val socPercent: Double, val from: LatLon)

    override suspend fun doWork(params: Params): TripPlanResult? {
        updateArrivalSoc(UpdateArrivalSocInteractor.Params(params.socPercent)).getOrThrow()
        val destination = trips.state.value.planned?.destination ?: return null
        return planTrip(PlanTripInteractor.Params(params.from, destination)).getOrThrow()
    }
}
