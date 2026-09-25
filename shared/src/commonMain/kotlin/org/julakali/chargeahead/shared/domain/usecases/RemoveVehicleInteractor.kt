package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SettingsStore

class RemoveVehicleInteractor(
    private val settings: SettingsStore,
) : Interactor<RemoveVehicleInteractor.Params, Unit>() {

    data class Params(val displayName: String)

    override suspend fun doWork(params: Params) {
        settings.removeVehicle(params.displayName)
    }
}
