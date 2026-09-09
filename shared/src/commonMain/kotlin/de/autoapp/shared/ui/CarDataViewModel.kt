package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.domain.CarDataPoint
import de.autoapp.shared.domain.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the car hardware last delivered, one entry per data point. */
data class CarDataUiState(val points: List<CarDataPoint> = emptyList())

/**
 * The car-data debug view: written by the Android Auto session, read here at
 * the desk afterwards. Read-only — this screen changes nothing.
 */
class CarDataViewModel(
    settings: SettingsStore,
) : ViewModel() {

    val uiState: StateFlow<CarDataUiState> = settings.carDebugData
        .map { CarDataUiState(points = it) }
        .stateIn(viewModelScope, WhileUiSubscribed, CarDataUiState())
}
