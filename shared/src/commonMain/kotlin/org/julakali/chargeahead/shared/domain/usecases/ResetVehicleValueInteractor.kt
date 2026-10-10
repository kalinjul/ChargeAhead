package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.VehicleCatalogRepository
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.customizedAgainst
import org.julakali.chargeahead.shared.domain.presetOf

/** Puts one value of the selected car back on the catalog's; the driver's other values stay. */
class ResetVehicleValueInteractor(
    private val vehicles: VehicleRepository,
    private val catalog: VehicleCatalogRepository,
) : Interactor<ResetVehicleValueInteractor.Params, Unit>() {

    enum class Value { NAME, BATTERY, DC_PEAK, CONSUMPTION }

    data class Params(val value: Value)

    override suspend fun doWork(params: Params) {
        val car = vehicles.vehicle.first() ?: return
        val catalog = catalog.presets.first().presetOf(car)?.toProfile() ?: return
        val reset = when (params.value) {
            Value.NAME -> car.copy(displayName = catalog.displayName, ownName = false)
            Value.BATTERY -> car.copy(usableBatteryKwh = catalog.usableBatteryKwh).customizedAgainst(catalog)
            Value.DC_PEAK -> car.copy(dcPeakPowerKw = catalog.dcPeakPowerKw).customizedAgainst(catalog)
            Value.CONSUMPTION -> car.copy(consumptionKwhPer100Km = catalog.consumptionKwhPer100Km, ownConsumption = false)
        }
        if (reset != car) vehicles.setVehicle(reset)
    }
}
