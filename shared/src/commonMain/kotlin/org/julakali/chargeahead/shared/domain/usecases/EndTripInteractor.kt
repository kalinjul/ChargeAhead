package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SettingsStore

/** The driver is done with the committed trip; nothing keeps it. */
class EndTripInteractor(
    private val settings: SettingsStore,
) : Interactor<Unit, Unit>() {

    override suspend fun doWork(params: Unit) {
        settings.clearCommittedTrip()
    }
}
