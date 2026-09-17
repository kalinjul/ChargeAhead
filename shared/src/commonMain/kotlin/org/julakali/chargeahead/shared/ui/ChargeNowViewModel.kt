package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.PlanningFeature
import org.julakali.chargeahead.shared.core.ChargeNowResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The best chargers around the current position, ranked. */
sealed interface ChargeNowUiState {

    /** No location yet. */
    data object NoPosition : ChargeNowUiState

    data object Loading : ChargeNowUiState

    data class Ready(val result: ChargeNowResult) : ChargeNowUiState
}

/**
 * "Charge now": ranks what is nearby against the driver's filters (see
 * [PlanningFeature.chargeNow]). Runs on demand.
 */
class ChargeNowViewModel(
    private val feature: ChargeStopsFeature,
    private val planning: PlanningFeature,
) : ViewModel() {

    private val state = MutableStateFlow<ChargeNowUiState>(ChargeNowUiState.NoPosition)
    val uiState: StateFlow<ChargeNowUiState> = state.asStateFlow()

    private var loadJob: Job? = null

    /** The sheet was opened: rank from the current position. */
    fun onSheetOpened() {
        val position = feature.currentState.position
        loadJob?.cancel()
        if (position == null) {
            state.value = ChargeNowUiState.NoPosition
            return
        }
        state.value = ChargeNowUiState.Loading
        loadJob = viewModelScope.launch {
            state.value = ChargeNowUiState.Ready(planning.chargeNow(position))
        }
    }
}
