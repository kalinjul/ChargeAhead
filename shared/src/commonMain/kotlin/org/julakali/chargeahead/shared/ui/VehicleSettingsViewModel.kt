package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleCatalogRepository
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.CarDiagnosticsRepository
import org.julakali.chargeahead.shared.domain.SoCDiagnostics
import org.julakali.chargeahead.shared.domain.presetOf
import org.julakali.chargeahead.shared.domain.reportedByCar
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.newVehicleId
import org.julakali.chargeahead.shared.domain.usecases.RestoreCatalogValuesInteractor
import org.julakali.chargeahead.shared.domain.usecases.SelectVehicleInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

/**
 * The free-form vehicle entry, field by field. Keeps the raw text, so
 * intermediate input like "17," survives.
 */
data class VehicleSettingsUiState(
    val name: String = "",
    val battery: String = "",
    val consumption: String = "",
    val connectors: Set<ConnectorType> = emptySet(),
    // Not editable here, but kept across edits.
    val dcPeakPowerKw: Double? = null,
    val modelId: String? = null,
    val id: String = "",
    /** A catalog car the driver changed, whose catalog values can be restored. */
    val canRestoreCatalogValues: Boolean = false,
    /** The stored level, shown only; the car or the planning dialogs set it. */
    val socInput: String = "",
    val socFromCar: Boolean = false,
    val diagnostics: SoCDiagnostics? = null,
) {
    val batteryInvalid: Boolean get() = battery.isNotBlank() && battery.toPositiveDoubleOrNull() == null
    val consumptionInvalid: Boolean get() = consumption.isNotBlank() && consumption.toPositiveDoubleOrNull() == null
}

/**
 * The advanced vehicle screen: capacity, consumption, connectors, charge
 * level. Every valid change is written through immediately.
 */
class VehicleSettingsViewModel(
    vehicles: VehicleRepository,
    catalog: VehicleCatalogRepository,
    diagnostics: CarDiagnosticsRepository,
    feature: ChargeStopsFeature,
    private val selectVehicle: SelectVehicleInteractor,
    private val restoreCatalogValues: RestoreCatalogValuesInteractor,
) : ViewModel() {

    // null = untouched, the form mirrors what is stored.
    private val form = MutableStateFlow<Form?>(null)

    val uiState: StateFlow<VehicleSettingsUiState> = combine(
        form,
        combine(vehicles.vehicle, catalog.presets) { vehicle, presets ->
            vehicle to (vehicle?.customized == true && presets.presetOf(vehicle) != null)
        },
        vehicles.manualSocPercent,
        diagnostics.socDiagnostics,
        feature.currentEnergy,
    ) { form, (vehicle, canRestore), socPercent, diagnostics, energy ->
        val edited = form ?: Form(
            name = vehicle?.displayName.orEmpty(),
            battery = vehicle?.usableBatteryKwh?.asInput().orEmpty(),
            consumption = vehicle?.consumptionKwhPer100Km?.asInput().orEmpty(),
            connectors = vehicle?.acceptedConnectors ?: emptySet(),
            dcPeakPowerKw = vehicle?.dcPeakPowerKw,
            modelId = vehicle?.modelId,
            id = vehicle?.id ?: newVehicleId(),
        )
        VehicleSettingsUiState(
            name = edited.name,
            battery = edited.battery,
            consumption = edited.consumption,
            connectors = edited.connectors,
            dcPeakPowerKw = edited.dcPeakPowerKw,
            modelId = edited.modelId,
            id = edited.id,
            canRestoreCatalogValues = canRestore,
            socInput = socPercent?.asInput().orEmpty(),
            socFromCar = energy.reportedByCar,
            diagnostics = diagnostics,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, VehicleSettingsUiState())

    fun onNameChanged(name: String) = edit { it.copy(name = name) }

    fun onBatteryChanged(battery: String) = edit { it.copy(battery = battery) }

    fun onConsumptionChanged(consumption: String) = edit { it.copy(consumption = consumption) }

    fun onConnectorToggled(type: ConnectorType, accepted: Boolean) = edit {
        it.copy(connectors = if (accepted) it.connectors + type else it.connectors - type)
    }

    /** Clears the profile and the form — the driver starts over. */
    fun onVehicleCleared() {
        form.value = Form()
        viewModelScope.launch { selectVehicle(SelectVehicleInteractor.Params(null)) }
    }

    /** Drops the driver's changes; the car follows the catalog again. */
    fun onCatalogValuesRestored() {
        form.value = null
        viewModelScope.launch { restoreCatalogValues(Unit) }
    }

    private fun edit(change: (Form) -> Form) {
        val updated = editForm(change)
        // Incomplete input stores no profile.
        val battery = updated.battery.toPositiveDoubleOrNull()
        val consumption = updated.consumption.toPositiveDoubleOrNull()
        val profile = if (battery == null || consumption == null) {
            null
        } else {
            VehicleProfile(
                displayName = updated.name.trim(),
                usableBatteryKwh = battery,
                consumptionKwhPer100Km = consumption,
                acceptedConnectors = updated.connectors,
                dcPeakPowerKw = updated.dcPeakPowerKw,
                modelId = updated.modelId,
                customized = updated.modelId != null,
                id = updated.id,
            )
        }
        viewModelScope.launch { selectVehicle(SelectVehicleInteractor.Params(profile)) }
    }

    private fun editForm(change: (Form) -> Form): Form {
        val updated = change(form.value ?: currentFromStore())
        form.value = updated
        return updated
    }

    private fun currentFromStore(): Form = uiState.value.let {
        Form(it.name, it.battery, it.consumption, it.connectors, it.dcPeakPowerKw, it.modelId, it.id)
    }

    private data class Form(
        val name: String = "",
        val battery: String = "",
        val consumption: String = "",
        val connectors: Set<ConnectorType> = emptySet(),
        val dcPeakPowerKw: Double? = null,
        val modelId: String? = null,
        val id: String = newVehicleId(),
    )
}

/** Accepts the German decimal comma. */
internal fun String.toPositiveDoubleOrNull(): Double? =
    replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0.0 }

/** One decimal at most, whole numbers without the ".0". */
internal fun Double.asInput(): String {
    val oneDecimal = round(this * 10) / 10
    val whole = round(oneDecimal)
    return if (abs(oneDecimal - whole) < 0.001) whole.toLong().toString() else oneDecimal.toString()
}
