package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.SettingsStore
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
    private val settings: SettingsStore,
    private val trips: TripRepository,
    private val updateManualSoc: UpdateManualSocInteractor,
    private val time: TimeProvider,
) : Interactor<ReplanCommittedTripInteractor.Params, TripPlanResult?>() {

    /** A [socPercent] is stored as the manual level; `null` takes the stored one. */
    data class Params(val from: LatLon, val socPercent: Double? = null)

    override suspend fun doWork(params: Params): TripPlanResult? {
        val destination = trips.state.value.committed?.plan?.destination ?: return null
        params.socPercent?.let { updateManualSoc(UpdateManualSocInteractor.Params(it)).getOrThrow() }
        val result = planner.planWithSettings(settings, params.from, destination, params.socPercent)
        if (result is TripPlanResult.Planned) {
            val plannedWith = params.socPercent ?: settings.manualSocPercent.first()
            val trip = CommittedTrip(result.plan, plannedWith, time.nowMillis())
            trips.update { it.replanned(trip) }
        }
        return result
    }
}
