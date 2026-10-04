package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.VehicleCatalogRepository
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.followingCatalog

/** Fetches the catalog and brings the garage's catalog cars up to date with it. */
class RefreshVehicleCatalogInteractor(
    private val repository: VehicleCatalogRepository,
    private val vehicles: VehicleRepository,
) : Interactor<Unit, Unit>() {

    override suspend fun doWork(params: Unit) {
        repository.refresh()
        val presets = repository.presets.first()
        vehicles.updateVehicles { it.followingCatalog(presets) }
    }
}
