package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.PlanningFeature
import de.autoapp.shared.core.ChargeNowResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The best chargers around the current position, ranked. */
sealed interface ChargeNowUiState {

    /** No location yet — nothing to rank against. */
    data object NoPosition : ChargeNowUiState

    data object Loading : ChargeNowUiState

    data class Ready(val result: ChargeNowResult) : ChargeNowUiState
}

/**
 * "Charge now": ranks what is nearby against the driver's filters, relaxing
 * them step by step until something remains (see [PlanningFeature.chargeNow]).
 *
 * Runs on demand rather than continuously — the ranking is only interesting
 * while the sheet is open, and it costs a database sweep and price quotes.
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
