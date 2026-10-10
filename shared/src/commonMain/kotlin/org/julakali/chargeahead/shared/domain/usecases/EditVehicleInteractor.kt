package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import kotlin.math.abs
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.VehicleCatalogRepository
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.presetOf

class EditVehicleInteractor(
    private val vehicles: VehicleRepository,
    private val catalog: VehicleCatalogRepository,
) : Interactor<EditVehicleInteractor.Params, Unit>() {

    sealed interface Edit {
        data class Name(val name: String) : Edit
        data class Battery(val usableKwh: Double) : Edit
        data class DcPeak(val kw: Double) : Edit
        data class Consumption(val kwhPer100Km: Double) : Edit
    }

    data class Params(val edit: Edit)

    override suspend fun doWork(params: Params) {
        val car = vehicles.vehicle.first() ?: return
        val catalog = catalog.presets.first().presetOf(car)?.toProfile()
        val edited = when (val edit = params.edit) {
            is Edit.Name -> edit.name.trim().let { name -> car.copy(displayName = name, ownName = catalog != null && name != catalog.displayName) }
            is Edit.Battery -> car.copy(usableBatteryKwh = edit.usableKwh).withCustomizedAgainst(catalog)
            is Edit.DcPeak -> car.copy(dcPeakPowerKw = edit.kw).withCustomizedAgainst(catalog)
            is Edit.Consumption -> {
                val datasheet = catalog?.consumptionKwhPer100Km
                // The slider works in Float; within a hair of the datasheet it is the datasheet.
                val onDatasheet = datasheet != null && abs(edit.kwhPer100Km - datasheet) < DATASHEET_TOLERANCE
                car.copy(
                    consumptionKwhPer100Km = if (onDatasheet) datasheet else edit.kwhPer100Km,
                    ownConsumption = if (datasheet != null) !onDatasheet else car.ownConsumption,
                )
            }
        }
        if (edited != car) vehicles.setVehicle(edited)
    }

    /** An own battery or power value takes the car off catalog updates; the catalog's value puts it back. */
    private fun VehicleProfile.withCustomizedAgainst(catalog: VehicleProfile?): VehicleProfile =
        copy(customized = catalog != null && (usableBatteryKwh != catalog.usableBatteryKwh || dcPeakPowerKw != catalog.dcPeakPowerKw))

    private companion object {
        /** Far below the slider's half-kWh steps, far above Float's rounding. */
        const val DATASHEET_TOLERANCE = 0.01
    }
}
