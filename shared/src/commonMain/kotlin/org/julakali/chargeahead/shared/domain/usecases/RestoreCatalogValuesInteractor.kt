package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.VehicleCatalogRepository
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.presetOf

/** Drops the driver's changes to the selected car; it follows the catalog again. */
class RestoreCatalogValuesInteractor(
    private val vehicles: VehicleRepository,
    private val catalog: VehicleCatalogRepository,
) : Interactor<Unit, Unit>() {

    override suspend fun doWork(params: Unit) {
        val vehicle = vehicles.vehicle.first() ?: return
        val preset = catalog.presets.first().presetOf(vehicle) ?: return
        vehicles.setVehicle(preset.toProfile().copy(id = vehicle.id))
    }
}
