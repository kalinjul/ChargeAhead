package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.VehicleProfile

/** Selects a vehicle, adding it to the garage; `null` clears the selection. */
class SelectVehicleInteractor(
    private val settings: SettingsStore,
) : Interactor<SelectVehicleInteractor.Params, Unit>() {

    data class Params(val profile: VehicleProfile?)

    override suspend fun doWork(params: Params) {
        settings.setVehicle(params.profile)
    }
}
