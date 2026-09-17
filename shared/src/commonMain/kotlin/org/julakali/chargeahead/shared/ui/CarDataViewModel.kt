package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.CarDataPoint
import org.julakali.chargeahead.shared.domain.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the car hardware last delivered, one entry per data point. */
data class CarDataUiState(val points: List<CarDataPoint> = emptyList())

/** The read-only car-data debug view. */
class CarDataViewModel(
    settings: SettingsStore,
) : ViewModel() {

    val uiState: StateFlow<CarDataUiState> = settings.carDebugData
        .map { CarDataUiState(points = it) }
        .stateIn(viewModelScope, WhileUiSubscribed, CarDataUiState())
}
