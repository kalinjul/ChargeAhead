package org.julakali.chargeahead.shared.ui.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.TripState
import org.julakali.chargeahead.shared.domain.usecases.PlanTripInteractor
import org.julakali.chargeahead.shared.ui.WhileUiSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CarRouteUiState {

    data class Loading(val waitingForLocation: Boolean) : CarRouteUiState

    /** Never [TripPlanResult.Planned]. */
    data class Failed(val result: TripPlanResult) : CarRouteUiState

    /** No stops means the destination is in reach without charging. */
    data class Ready(val plan: TripPlan) : CarRouteUiState

    /** The active route ended elsewhere; the screen leaves. */
    data object Ended : CarRouteUiState
}

/**
 * The route to [destination] with its charging stops, planned here with the
 * live charge state — or, for an [activeRoute], the committed trip as the
 * phone keeps it.
 */
class CarRouteViewModel(
    private val feature: ChargeStopsFeature,
    private val destination: Destination,
    private val activeRoute: Boolean,
    private val planTrip: PlanTripInteractor,
    trips: TripRepository,
) : ViewModel() {

    /** Why the last attempt found no plan; `null` after a success. */
    private val failure = MutableStateFlow<TripPlanResult?>(null)

    val uiState: StateFlow<CarRouteUiState> = combine(
        trips.state,
        planTrip.inProgress,
        failure,
        feature.currentFix,
    ) { state, planning, failed, fix ->
        val plan = shownPlan(state)
        when {
            activeRoute && state.committed == null -> CarRouteUiState.Ended
            planning -> CarRouteUiState.Loading(waitingForLocation = fix == null)
            failed != null -> CarRouteUiState.Failed(failed)
            plan == null -> CarRouteUiState.Loading(waitingForLocation = fix == null)
            else -> CarRouteUiState.Ready(plan)
        }
    }.stateIn(viewModelScope, WhileUiSubscribed, CarRouteUiState.Loading(waitingForLocation = true))

    init {
        if (!activeRoute) {
            viewModelScope.launch { plan(feature.currentFix.filterNotNull().first()) }
        }
    }

    // A trip the phone plans to the same destination counts too.
    private fun shownPlan(state: TripState): TripPlan? =
        if (activeRoute) {
            state.committed?.plan
        } else {
            state.planned?.takeIf { it.destination.position == destination.position }
        }

    /** Plans again with the freshest position and charge state. */
    fun onRefresh() {
        val fix = feature.currentFix.value ?: return
        viewModelScope.launch { plan(fix) }
    }

    private suspend fun plan(fix: Fix) {
        val result = planTrip(
            PlanTripInteractor.Params(
                from = fix.position,
                destination = destination,
                startSocPercent = feature.currentEnergy.value?.socPercent,
            ),
        ).getOrDefault(TripPlanResult.NoRoute)
        failure.value = result.takeUnless { it is TripPlanResult.Planned }
    }
}
