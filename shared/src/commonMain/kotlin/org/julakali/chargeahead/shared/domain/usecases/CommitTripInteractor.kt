package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.TripPlan

/** Makes [Params.plan] the trip the driver is on; the previous one, if any, is replaced. */
class CommitTripInteractor(
    private val settings: SettingsStore,
    private val time: TimeProvider,
) : Interactor<CommitTripInteractor.Params, CommittedTrip>() {

    data class Params(val plan: TripPlan, val startSocPercent: Double?)

    override suspend fun doWork(params: Params): CommittedTrip {
        val trip = CommittedTrip(params.plan, params.startSocPercent, time.nowMillis())
        settings.commitTrip(trip)
        return trip
    }
}
