package org.julakali.chargeahead.shared.ui.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.usecases.ChargeNowObserver
import org.julakali.chargeahead.shared.domain.usecases.RefreshChargeNowInteractor
import org.julakali.chargeahead.shared.ui.ChargeNowUiState
import org.julakali.chargeahead.shared.ui.WhileUiSubscribed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "Charge now" in the car: ranks from the first position that arrives, and again on refresh. */
class CarChargeNowViewModel(
    internal val feature: ChargeStopsFeature,
    private val observeChargeNow: ChargeNowObserver,
    private val refreshChargeNow: RefreshChargeNowInteractor,
) : ViewModel() {

    private var refreshJob: Job? = null

    val uiState: StateFlow<ChargeNowUiState> = combine(
        observeChargeNow.flow,
        refreshChargeNow.inProgress,
        feature.currentFix,
    ) { result, refreshing, fix ->
        when {
            fix == null -> ChargeNowUiState.NoPosition
            // Nothing stored yet: wait for the refill rather than say "nothing nearby".
            result == null || result.isEmpty && refreshing -> ChargeNowUiState.Loading
            else -> ChargeNowUiState.Ready(result)
        }
    }.stateIn(viewModelScope, WhileUiSubscribed, ChargeNowUiState.NoPosition)

    init {
        observeChargeNow(ChargeNowObserver.Params(position = null))
        viewModelScope.launch { load(feature.currentFix.filterNotNull().first()) }
    }

    fun onRefresh() {
        feature.currentFix.value?.let(::load)
    }

    private fun load(fix: Fix) {
        observeChargeNow(ChargeNowObserver.Params(fix.position))
        refreshJob?.cancel()
        // A failed refill leaves the stored sites to rank.
        refreshJob = viewModelScope.launch { refreshChargeNow(RefreshChargeNowInteractor.Params(fix.position)) }
    }
}
