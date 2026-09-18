package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.ChargeNowResult
import org.julakali.chargeahead.shared.domain.ObserveChargeNow
import org.julakali.chargeahead.shared.domain.RefreshChargeNow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The best chargers around the current position, ranked. */
sealed interface ChargeNowUiState {

    /** No location yet. */
    data object NoPosition : ChargeNowUiState

    data object Loading : ChargeNowUiState

    data class Ready(val result: ChargeNowResult) : ChargeNowUiState
}

/** "Charge now": ranks what is nearby against the driver's filters, from where the sheet was opened. */
class ChargeNowViewModel(
    private val feature: ChargeStopsFeature,
    private val observeChargeNow: ObserveChargeNow,
    private val refreshChargeNow: RefreshChargeNow,
) : ViewModel() {

    private var refreshJob: Job? = null

    val uiState: StateFlow<ChargeNowUiState> = combine(
        observeChargeNow.flow,
        refreshChargeNow.inProgress,
    ) { result, refreshing ->
        when {
            result == null -> ChargeNowUiState.NoPosition
            // Nothing stored yet: wait for the refill rather than say "nothing nearby".
            result.isEmpty && refreshing -> ChargeNowUiState.Loading
            else -> ChargeNowUiState.Ready(result)
        }
    }.stateIn(viewModelScope, WhileUiSubscribed, ChargeNowUiState.NoPosition)

    init {
        observeChargeNow(ObserveChargeNow.Params(position = null))
    }

    /** The sheet was opened: rank from the current position. */
    fun onSheetOpened() {
        val position = feature.currentFix.value?.position
        observeChargeNow(ObserveChargeNow.Params(position))
        refreshJob?.cancel()
        // A failed refill leaves the stored sites to rank.
        refreshJob = position?.let {
            viewModelScope.launch { refreshChargeNow(RefreshChargeNow.Params(it)) }
        }
    }
}
