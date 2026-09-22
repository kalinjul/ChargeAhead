package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.combine
import org.julakali.chargeahead.shared.domain.DEFAULT_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.MAX_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.VehicleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The driver's cars and the charge levels the selected one plans with. */
data class GarageUiState(
    val vehicles: List<VehicleProfile> = emptyList(),
    val selected: VehicleProfile? = null,
    val socPercent: Double? = null,
    /** The charge level comes from the car, so the slider is locked. */
    val socFromCar: Boolean = false,
    /** How full the battery should still be at the destination. */
    val arrivalSocPercent: Double = DEFAULT_ARRIVAL_SOC_PERCENT,
    /** The arrival-level dialog's entry; `null` while it is closed. */
    val arrivalSocInput: String? = null,
)

/** The garage screen: choose, edit, remove a car. */
class GarageViewModel(
    private val settings: SettingsStore,
    feature: ChargeStopsFeature,
) : ViewModel() {

    private val arrivalSocEditor = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GarageUiState> = combine(
        settings.vehicles,
        settings.vehicle,
        settings.manualSocPercent,
        settings.arrivalSocPercent,
        feature.currentEnergy,
        arrivalSocEditor,
    ) { vehicles, selected, socPercent, arrivalSoc, energy, arrivalEditor ->
        GarageUiState(
            vehicles = vehicles,
            selected = selected,
            socPercent = socPercent,
            socFromCar = energy?.source == SoCSourceKind.CAR_HARDWARE,
            arrivalSocPercent = arrivalSoc,
            arrivalSocInput = arrivalEditor,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, GarageUiState())

    /** Selecting also stores an edited profile. */
    fun onVehicleSelected(profile: VehicleProfile) {
        viewModelScope.launch { settings.setVehicle(profile) }
    }

    fun onVehicleRemoved(displayName: String) {
        viewModelScope.launch { settings.removeVehicle(displayName) }
    }

    fun onSocChanged(socPercent: Double) {
        viewModelScope.launch { settings.setManualSocPercent(socPercent) }
    }

    /** Opens the arrival-level dialog on the level currently in force. */
    fun onArrivalSocEditRequested() {
        viewModelScope.launch {
            arrivalSocEditor.value = settings.arrivalSocPercent.first().roundToInt().toString()
        }
    }

    fun onArrivalSocInputChanged(input: String) {
        // Only while the dialog is open: a stray keystroke must not reopen it.
        arrivalSocEditor.update { open -> open?.let { input.filter(Char::isDigit).take(3) } }
    }

    fun onArrivalSocEditDismissed() {
        arrivalSocEditor.value = null
    }

    fun onArrivalSocConfirmed() {
        val entered = arrivalSocEditor.value?.toIntOrNull()?.takeIf { it in ARRIVAL_SOC_RANGE } ?: return
        arrivalSocEditor.value = null
        viewModelScope.launch { settings.setArrivalSocPercent(entered.toDouble()) }
    }
}

/** What the planner can honour as an arrival level — see [MAX_ARRIVAL_SOC_PERCENT]. */
val ARRIVAL_SOC_RANGE = 0..MAX_ARRIVAL_SOC_PERCENT.toInt()
