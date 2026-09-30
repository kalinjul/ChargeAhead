package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.VehicleRepository

/** Stores how full the battery should still be at the destination. */
class UpdateArrivalSocInteractor(
    private val vehicles: VehicleRepository,
) : Interactor<UpdateArrivalSocInteractor.Params, Unit>() {

    data class Params(val socPercent: Double)

    override suspend fun doWork(params: Params) {
        vehicles.setArrivalSocPercent(params.socPercent)
    }
}
