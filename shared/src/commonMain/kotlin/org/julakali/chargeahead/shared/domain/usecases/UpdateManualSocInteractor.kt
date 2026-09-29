package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.VehicleRepository

/** Stores the charge level the driver typed in; `null` means nothing entered. */
class UpdateManualSocInteractor(
    private val vehicles: VehicleRepository,
) : Interactor<UpdateManualSocInteractor.Params, Unit>() {

    data class Params(val socPercent: Double?)

    override suspend fun doWork(params: Params) {
        vehicles.setManualSocPercent(params.socPercent)
    }
}
