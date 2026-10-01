package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.VehicleCatalogRepository

class RefreshVehicleCatalogInteractor(
    private val repository: VehicleCatalogRepository,
) : Interactor<Unit, Unit>() {

    override suspend fun doWork(params: Unit) = repository.refresh()
}
