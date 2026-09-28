package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.TripRepository

/** The driver closed the planned trip; the destination stays. */
class DismissPlannedTripInteractor(
    private val trips: TripRepository,
) : Interactor<Unit, Unit>() {

    override suspend fun doWork(params: Unit) {
        trips.update { it.planDismissed() }
    }
}
