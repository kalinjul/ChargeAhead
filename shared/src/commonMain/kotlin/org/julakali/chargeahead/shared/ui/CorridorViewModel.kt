package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.ChargeStopsState
import org.julakali.chargeahead.shared.ChargeStopsState.FailureReason
import org.julakali.chargeahead.shared.ChargeStopsState.Phase
import org.julakali.chargeahead.shared.domain.ChargeStops
import org.julakali.chargeahead.shared.domain.usecases.ChargeStopsObserver
import org.julakali.chargeahead.shared.domain.usecases.RefreshChargeStopsInteractor
import org.julakali.chargeahead.shared.domain.RouteStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The corridor list and its status, as the iOS list screens show it. */
class CorridorViewModel(
    private val feature: ChargeStopsFeature,
    private val observeChargeStops: ChargeStopsObserver,
    private val refreshChargeStops: RefreshChargeStopsInteractor,
) : ViewModel() {

    private val chargeStops: StateFlow<ChargeStops?> =
        observeChargeStops.flow.stateIn(viewModelScope, WhileUiSubscribed, null)

    val uiState: StateFlow<ChargeStopsState> = combine(
        chargeStops,
        refreshChargeStops.inProgress,
        feature.currentFix,
        feature.locationFailed,
    ) { stops, refreshing, fix, locationFailed ->
        val (phase, failure) = when {
            locationFailed -> Phase.FAILED to FailureReason.LOCATION_UNAVAILABLE
            fix == null -> Phase.WAITING_FOR_LOCATION to null
            stops == null || refreshing || stops.refill == ChargeStops.Refill.RUNNING -> Phase.LOADING to null
            stops.refill == ChargeStops.Refill.FAILED -> Phase.FAILED to FailureReason.SITES_UNAVAILABLE
            else -> Phase.READY to null
        }
        ChargeStopsState(
            stops = stops?.stops.orEmpty(),
            phase = phase,
            failure = failure,
            socSource = stops?.socSource,
            destination = stops?.destination,
            routeStatus = stops?.routeStatus ?: RouteStatus.NONE,
            networkFilterActive = stops?.networkFilterActive ?: false,
            position = fix?.position,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, ChargeStopsState())

    init {
        observeChargeStops(ChargeStopsObserver.Params(feature.currentFix, feature.currentEnergy))
    }

    /** Discards the stock and searches the current area again. Before the first fix, the first search does that anyway. */
    fun onRefresh() {
        val area = chargeStops.value?.area ?: return
        viewModelScope.launch { refreshChargeStops(RefreshChargeStopsInteractor.Params(area)) }
    }
}
