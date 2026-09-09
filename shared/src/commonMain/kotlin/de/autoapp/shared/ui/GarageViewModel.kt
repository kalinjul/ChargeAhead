package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.SoCSourceKind
import de.autoapp.shared.domain.VehicleProfile
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The driver's cars and the charge level the selected one is at. */
data class GarageUiState(
    val vehicles: List<VehicleProfile> = emptyList(),
    val selected: VehicleProfile? = null,
    val socPercent: Double? = null,
    /**
     * The charge level comes from the car, so the slider is locked — a
     * value typed here would be silently overwritten on the next update.
     */
    val socFromCar: Boolean = false,
)

/** The garage screen: choose, edit, remove a car. */
class GarageViewModel(
    private val settings: SettingsStore,
    feature: ChargeStopsFeature,
) : ViewModel() {

    val uiState: StateFlow<GarageUiState> = combine(
        settings.vehicles,
        settings.vehicle,
        settings.manualSocPercent,
        feature.state,
    ) { vehicles, selected, socPercent, state ->
        GarageUiState(
            vehicles = vehicles,
            selected = selected,
            socPercent = socPercent,
            socFromCar = state.socSource == SoCSourceKind.CAR_HARDWARE,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, GarageUiState())

    /** Selecting also stores: an edited profile is written back through the same call. */
    fun onVehicleSelected(profile: VehicleProfile) {
        viewModelScope.launch { settings.setVehicle(profile) }
    }

    fun onVehicleRemoved(displayName: String) {
        viewModelScope.launch { settings.removeVehicle(displayName) }
    }

    fun onSocChanged(socPercent: Double) {
        viewModelScope.launch { settings.setManualSocPercent(socPercent) }
    }
}
