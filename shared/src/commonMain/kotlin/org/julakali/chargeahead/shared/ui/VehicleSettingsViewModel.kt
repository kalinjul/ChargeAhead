package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.CarDiagnosticsRepository
import org.julakali.chargeahead.shared.domain.SoCDiagnostics
import org.julakali.chargeahead.shared.domain.reportedByCar
import org.julakali.chargeahead.shared.domain.VehicleProfile
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
    diagnostics: CarDiagnosticsRepository,
    feature: ChargeStopsFeature,
    private val selectVehicle: SelectVehicleInteractor,
) : ViewModel() {

    // null = untouched, the form mirrors what is stored.
    private val form = MutableStateFlow<Form?>(null)

    val uiState: StateFlow<VehicleSettingsUiState> = combine(
        form,
        vehicles.vehicle,
        vehicles.manualSocPercent,
        diagnostics.socDiagnostics,
        feature.currentEnergy,
    ) { form, vehicle, socPercent, diagnostics, energy ->
        val edited = form ?: Form(
            name = vehicle?.displayName.orEmpty(),
            battery = vehicle?.usableBatteryKwh?.asInput().orEmpty(),
            consumption = vehicle?.consumptionKwhPer100Km?.asInput().orEmpty(),
            connectors = vehicle?.acceptedConnectors ?: emptySet(),
        )
        VehicleSettingsUiState(
            name = edited.name,
            battery = edited.battery,
            consumption = edited.consumption,
            connectors = edited.connectors,
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

    private fun edit(change: (Form) -> Form) {
        val updated = editForm(change)
        // Incomplete input stores no profile.
        val battery = updated.battery.toPositiveDoubleOrNull()
        val consumption = updated.consumption.toPositiveDoubleOrNull()
        val profile = if (battery == null || consumption == null) {
            null
        } else {
            VehicleProfile(updated.name.trim(), battery, consumption, updated.connectors)
        }
        viewModelScope.launch { selectVehicle(SelectVehicleInteractor.Params(profile)) }
    }

    private fun editForm(change: (Form) -> Form): Form {
        val updated = change(form.value ?: currentFromStore())
        form.value = updated
        return updated
    }

    private fun currentFromStore(): Form = uiState.value.let {
        Form(it.name, it.battery, it.consumption, it.connectors)
    }

    private data class Form(
        val name: String = "",
        val battery: String = "",
        val consumption: String = "",
        val connectors: Set<ConnectorType> = emptySet(),
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
