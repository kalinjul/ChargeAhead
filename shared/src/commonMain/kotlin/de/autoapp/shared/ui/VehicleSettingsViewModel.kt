package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.SoCDiagnostics
import de.autoapp.shared.domain.SoCSourceKind
import de.autoapp.shared.domain.VehicleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

/**
 * The free-form vehicle entry, field by field.
 *
 * The text is state, not just display: "17," is a legitimate intermediate
 * step towards "17,8" that parses to nothing. Keeping the raw text here is
 * what lets the driver type it — deriving the fields from the stored profile
 * instead would clear the form on that comma, because an unparseable form
 * stores no profile.
 */
data class VehicleSettingsUiState(
    val name: String = "",
    val battery: String = "",
    val consumption: String = "",
    val connectors: Set<ConnectorType> = emptySet(),
    val socInput: String = "",
    val socFromCar: Boolean = false,
    val diagnostics: SoCDiagnostics? = null,
) {
    val batteryInvalid: Boolean get() = battery.isNotBlank() && battery.toPositiveDoubleOrNull() == null
    val consumptionInvalid: Boolean get() = consumption.isNotBlank() && consumption.toPositiveDoubleOrNull() == null
    val socInvalid: Boolean get() = socInput.isNotBlank() && socInput.toPercentOrNull() == null
}

/**
 * The advanced vehicle screen: capacity, consumption, connectors, charge
 * level — deliberately without a vehicle list (ARCHITECTURE.md, open point 4).
 *
 * Every valid change is written through immediately, not on a "Save" button.
 * A charge level the driver typed and that failed to land because of a
 * forgotten tap would be the worst possible way for the reachability
 * calculation to go wrong.
 */
class VehicleSettingsViewModel(
    private val settings: SettingsStore,
    feature: ChargeStopsFeature,
) : ViewModel() {

    // null = the driver has not touched the form yet, so it still mirrors
    // what is stored. From the first keystroke on, the form is the truth.
    private val form = MutableStateFlow<Form?>(null)

    val uiState: StateFlow<VehicleSettingsUiState> = combine(
        form,
        settings.vehicle,
        settings.manualSocPercent,
        settings.socDiagnostics,
        feature.state,
    ) { form, vehicle, socPercent, diagnostics, state ->
        val edited = form ?: Form(
            name = vehicle?.displayName.orEmpty(),
            battery = vehicle?.usableBatteryKwh?.asInput().orEmpty(),
            consumption = vehicle?.consumptionKwhPer100Km?.asInput().orEmpty(),
            connectors = vehicle?.acceptedConnectors ?: emptySet(),
            socInput = socPercent?.asInput().orEmpty(),
        )
        VehicleSettingsUiState(
            name = edited.name,
            battery = edited.battery,
            consumption = edited.consumption,
            connectors = edited.connectors,
            socInput = edited.socInput,
            socFromCar = state.socSource == SoCSourceKind.CAR_HARDWARE,
            diagnostics = diagnostics,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, VehicleSettingsUiState())

    fun onNameChanged(name: String) = edit { it.copy(name = name) }

    fun onBatteryChanged(battery: String) = edit { it.copy(battery = battery) }

    fun onConsumptionChanged(consumption: String) = edit { it.copy(consumption = consumption) }

    fun onConnectorToggled(type: ConnectorType, accepted: Boolean) = edit {
        it.copy(connectors = if (accepted) it.connectors + type else it.connectors - type)
    }

    fun onSocChanged(socInput: String) {
        editForm { it.copy(socInput = socInput) }
        viewModelScope.launch { settings.setManualSocPercent(socInput.toPercentOrNull()) }
    }

    /** Clears the profile and the form — the driver starts over. */
    fun onVehicleCleared() {
        form.value = Form()
        viewModelScope.launch { settings.setVehicle(null) }
    }

    private fun edit(change: (Form) -> Form) {
        val updated = editForm(change)
        // Incomplete input stores no profile: a half-typed capacity must not
        // silently become the number every range calculation runs on.
        val battery = updated.battery.toPositiveDoubleOrNull()
        val consumption = updated.consumption.toPositiveDoubleOrNull()
        viewModelScope.launch {
            settings.setVehicle(
                if (battery == null || consumption == null) {
                    null
                } else {
                    VehicleProfile(updated.name.trim(), battery, consumption, updated.connectors)
                },
            )
        }
    }

    private fun editForm(change: (Form) -> Form): Form {
        // The first edit continues from what the screen was showing, which is
        // the stored profile; every later one from the form itself.
        val updated = change(form.value ?: currentFromStore())
        form.value = updated
        return updated
    }

    private fun currentFromStore(): Form = uiState.value.let {
        Form(it.name, it.battery, it.consumption, it.connectors, it.socInput)
    }

    private data class Form(
        val name: String = "",
        val battery: String = "",
        val consumption: String = "",
        val connectors: Set<ConnectorType> = emptySet(),
        val socInput: String = "",
    )
}

/** Accepts the German decimal comma — otherwise entering "17,8" would fail. */
internal fun String.toPositiveDoubleOrNull(): Double? =
    replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0.0 }

internal fun String.toPercentOrNull(): Double? =
    replace(',', '.').trim().toDoubleOrNull()?.takeIf { it in 0.0..100.0 }

/**
 * One decimal at most, whole numbers without the ".0" — 77 instead of 77.0,
 * 17.8 instead of a slider's 17.83400000001. A garage slider can store an
 * ugly float; the field must not echo it back.
 */
internal fun Double.asInput(): String {
    val oneDecimal = round(this * 10) / 10
    val whole = round(oneDecimal)
    return if (abs(oneDecimal - whole) < 0.001) whole.toLong().toString() else oneDecimal.toString()
}
