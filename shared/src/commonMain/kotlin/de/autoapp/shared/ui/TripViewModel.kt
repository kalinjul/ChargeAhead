package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.PlanningFeature
import de.autoapp.shared.core.TripPlan
import de.autoapp.shared.core.TripPlanResult
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.SavedRoute
import de.autoapp.shared.domain.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The planned trip, from "nothing planned yet" to the finished plan. */
sealed interface TripUiState {

    data object NoPlan : TripUiState

    data object Planning : TripUiState

    data class Planned(
        val plan: TripPlan,
        /** The route is in the driver's favourites. */
        val isSaved: Boolean,
        /** At least one price is an estimate — the screen has to say so. */
        val isEstimate: Boolean,
        val startPosition: LatLon?,
        val startSocPercent: Double?,
        val selection: SectionSelection = SectionSelection(),
    ) : TripUiState
}

/**
 * Two picked points along start → stops → destination, for sending only a
 * section of the trip to Maps. Survives rotation because it lives here, not
 * in a `remember` (the viewmodels skill, rule 7).
 */
data class SectionSelection(
    val selecting: Boolean = false,
    val a: Int? = null,
    val b: Int? = null,
) {

    fun toggled(): SectionSelection =
        if (selecting) SectionSelection() else SectionSelection(selecting = true)

    /** First tap fills [a], the second [b]; a third starts a fresh pair. */
    fun picked(index: Int): SectionSelection = when {
        a == null -> copy(a = index)
        b == null && index != a -> copy(b = index)
        b == null -> this
        else -> copy(a = index, b = null)
    }
}

/**
 * What just happened, once. The UI turns it into a snackbar or a screen
 * change and then calls [TripViewModel.onEventHandled].
 *
 * No text: the reason travels as a type, the wording comes from the
 * platform's resources (AGENTS.md, language rule).
 */
sealed interface TripEvent {

    /** A plan is ready — the caller decides whether to navigate to it. */
    data object PlanReady : TripEvent

    data object VehicleMissing : TripEvent

    data class NoChargerInReach(val afterKm: Double) : TripEvent

    data object NoRoute : TripEvent

    data object RouteSaved : TripEvent

    data object RouteRemoved : TripEvent
}

/**
 * Planning a trip and everything the result screen shows: the plan itself,
 * whether it is saved, which charge level it started from.
 *
 * The plan is held here rather than recomputed per screen — planning costs a
 * routing request and a database sweep, and the stop detail is a zoom into
 * the same plan, not a new one.
 */
class TripViewModel(
    private val feature: ChargeStopsFeature,
    private val planning: PlanningFeature,
    private val settings: SettingsStore,
) : ViewModel() {

    private val currentPlan = MutableStateFlow<TripPlan?>(null)
    private val isPlanning = MutableStateFlow(false)
    private val selection = MutableStateFlow(SectionSelection())
    private val events = MutableStateFlow<TripEvent?>(null)

    val event: StateFlow<TripEvent?> = events.asStateFlow()

    // combine tops out at five typed flows — the plan-local trio is
    // pre-combined so every input keeps its type.
    private data class PlanInputs(
        val plan: TripPlan?,
        val planning: Boolean,
        val selection: SectionSelection,
    )

    val uiState: StateFlow<TripUiState> = combine(
        combine(currentPlan, isPlanning, selection, ::PlanInputs),
        settings.savedRoutes,
        settings.manualSocPercent,
        feature.state,
    ) { inputs, saved, socPercent, state ->
        val plan = inputs.plan
        when {
            inputs.planning -> TripUiState.Planning
            plan == null -> TripUiState.NoPlan
            else -> TripUiState.Planned(
                plan = plan,
                isSaved = saved.any { it.destination.position == plan.destination.position },
                isEstimate = plan.stops.any { it.quote.isEstimate },
                startPosition = state.position,
                startSocPercent = socPercent,
                selection = inputs.selection,
            )
        }
    }.stateIn(viewModelScope, WhileUiSubscribed, TripUiState.NoPlan)

    /**
     * Plans from the current position to [destination].
     *
     * A [socPercent] the driver just typed is persisted, not just used for
     * this one plan: the garage and the car UI read the same value, and the
     * next plan starts from it. `null` means "take the stored one" — that is
     * what reopening a saved route does.
     */
    fun plan(destination: Destination, socPercent: Double? = null) {
        val from = feature.currentState.position ?: return
        // Same semantics the screen's remember(plan) had: a new plan starts
        // with a clean selection.
        selection.value = SectionSelection()
        isPlanning.value = true
        viewModelScope.launch {
            socPercent?.let { settings.setManualSocPercent(it) }
            feature.setDestination(destination)
            when (val result = planning.planTrip(from, destination, socOverridePercent = socPercent)) {
                is TripPlanResult.Planned -> {
                    currentPlan.value = result.plan
                    events.value = TripEvent.PlanReady
                }

                is TripPlanResult.NoVehicle -> events.value = TripEvent.VehicleMissing

                is TripPlanResult.NoChargerInReach ->
                    events.value = TripEvent.NoChargerInReach(result.afterKm)

                is TripPlanResult.NoRoute -> events.value = TripEvent.NoRoute
            }
            isPlanning.value = false
        }
    }

    /**
     * Saves the current plan's destination as a favourite, or removes it
     * again.
     *
     * [summary] is user-visible text and therefore comes from the caller's
     * resources — `shared` has none.
     */
    fun toggleSaved(summary: String) {
        val current = currentPlan.value ?: return
        viewModelScope.launch {
            val existing = settings.savedRoutes.first()
                .firstOrNull { it.destination.position == current.destination.position }
            if (existing != null) {
                settings.removeSavedRoute(existing.id)
                events.value = TripEvent.RouteRemoved
            } else {
                settings.saveRoute(
                    SavedRoute(
                        id = current.destination.routeId(),
                        name = current.destination.name,
                        destination = current.destination,
                        summary = summary,
                    ),
                )
                events.value = TripEvent.RouteSaved
            }
        }
    }

    fun onSectionSelectingToggled() {
        selection.value = selection.value.toggled()
    }

    fun onSectionPointPicked(index: Int) {
        selection.value = selection.value.picked(index)
    }

    /** After a section went to Maps the mode ends, exactly as before. */
    fun onSectionSent() {
        selection.value = SectionSelection()
    }

    fun onEventHandled() {
        events.value = null
    }
}

/**
 * Stable identity of a saved route: the position, not the name. The same
 * place typed differently is the same route.
 */
fun Destination.routeId(): String = "dest:${position.lat},${position.lon}"
