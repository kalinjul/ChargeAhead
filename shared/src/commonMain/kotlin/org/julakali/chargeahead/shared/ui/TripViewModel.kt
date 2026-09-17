package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.PlanningFeature
import org.julakali.chargeahead.shared.core.TripPlan
import org.julakali.chargeahead.shared.core.TripPlanResult
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.SavedRoute
import org.julakali.chargeahead.shared.domain.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The planned trip, from "nothing planned yet" to the finished plan. */
sealed interface TripUiState {

    data object NoPlan : TripUiState

    data object Planning : TripUiState

    data class Planned(
        val plan: TripPlan,
        /** The route is in the driver's favourites. */
        val isSaved: Boolean,
        val startPosition: LatLon?,
        val startSocPercent: Double?,
        val selection: SectionSelection = SectionSelection(),
        /** The quick charge-level editor's input; `null` while it is closed. */
        val socInput: String? = null,
        /** The arrival-level editor's input; `null` while it is closed. */
        val arrivalSocInput: String? = null,
    ) : TripUiState
}

/**
 * Two picked points along start → stops → destination, for sending only a
 * section of the trip to Maps.
 */
data class SectionSelection(
    val selecting: Boolean = false,
    val a: Int? = null,
    val b: Int? = null,
) {

    fun toggled(): SectionSelection =
        if (selecting) SectionSelection() else SectionSelection(selecting = true)

    /** Every point that goes to Maps, including the stops between the two picked ones. */
    fun includes(index: Int): Boolean = when {
        a == null -> false
        b == null -> index == a
        else -> index in minOf(a, b)..maxOf(a, b)
    }

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
 */
sealed interface TripEvent {

    /** A plan is ready. */
    data object PlanReady : TripEvent

    data object VehicleMissing : TripEvent

    data class NoChargerInReach(val afterKm: Double) : TripEvent

    data object NoRoute : TripEvent

    data object RouteSaved : TripEvent

    data object RouteRemoved : TripEvent
}

/** Planning a trip and everything the result screen shows. */
class TripViewModel(
    private val feature: ChargeStopsFeature,
    private val planning: PlanningFeature,
    private val settings: SettingsStore,
) : ViewModel() {

    private val currentPlan = MutableStateFlow<TripPlan?>(null)
    private val isPlanning = MutableStateFlow(false)
    private val selection = MutableStateFlow(SectionSelection())
    private val socEditor = MutableStateFlow<String?>(null)
    private val arrivalSocEditor = MutableStateFlow<String?>(null)
    private val events = MutableStateFlow<TripEvent?>(null)

    val event: StateFlow<TripEvent?> = events.asStateFlow()

    // combine tops out at five typed flows.
    private data class PlanInputs(
        val plan: TripPlan?,
        val planning: Boolean,
        val selection: SectionSelection,
        val socInput: String?,
        val arrivalSocInput: String?,
    )

    val uiState: StateFlow<TripUiState> = combine(
        combine(currentPlan, isPlanning, selection, socEditor, arrivalSocEditor, ::PlanInputs),
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
                startPosition = state.position,
                startSocPercent = socPercent,
                selection = inputs.selection,
                socInput = inputs.socInput,
                arrivalSocInput = inputs.arrivalSocInput,
            )
        }
    }.stateIn(viewModelScope, WhileUiSubscribed, TripUiState.NoPlan)

    /**
     * Plans from the current position to [destination].
     *
     * A [socPercent] is persisted; `null` means "take the stored one".
     */
    fun plan(destination: Destination, socPercent: Double? = null) {
        val from = feature.currentState.position ?: return
        // A new plan starts with a clean selection and closed editors.
        selection.value = SectionSelection()
        socEditor.value = null
        arrivalSocEditor.value = null
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
     * again. [summary] comes from the caller's resources.
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

    /**
     * Opens the quick charge-level entry on the start row, seeded with the
     * level this plan was made from.
     */
    fun onStartSocEditRequested() {
        viewModelScope.launch {
            socEditor.value = settings.manualSocPercent.first()?.roundToInt()?.toString().orEmpty()
        }
    }

    fun onStartSocInputChanged(input: String) {
        // Only while the editor is open: a stray keystroke must not reopen it.
        socEditor.update { open -> open?.let { input.filter(Char::isDigit).take(3) } }
    }

    fun onStartSocEditDismissed() {
        socEditor.value = null
    }

    /** Re-plans the same destination from the charge level just entered. */
    fun onStartSocConfirmed() {
        val socPercent = socEditor.value?.toIntOrNull()?.takeIf { it in 1..100 } ?: return
        val destination = currentPlan.value?.destination ?: return
        socEditor.value = null
        plan(destination, socPercent.toDouble())
    }

    /** Opens the arrival-level editor on the level this plan was made with. */
    fun onArrivalSocEditRequested() {
        viewModelScope.launch {
            arrivalSocEditor.value = settings.arrivalSocPercent.first().roundToInt().toString()
        }
    }

    fun onArrivalSocInputChanged(input: String) {
        // Only while the editor is open: a stray keystroke must not reopen it.
        arrivalSocEditor.update { open -> open?.let { input.filter(Char::isDigit).take(3) } }
    }

    fun onArrivalSocEditDismissed() {
        arrivalSocEditor.value = null
    }

    /** Stores the arrival level just entered and re-plans the same destination. */
    fun onArrivalSocConfirmed() {
        val socPercent = arrivalSocEditor.value?.toIntOrNull()?.takeIf { it in ARRIVAL_SOC_RANGE } ?: return
        val destination = currentPlan.value?.destination ?: return
        arrivalSocEditor.value = null
        viewModelScope.launch {
            settings.setArrivalSocPercent(socPercent.toDouble())
            plan(destination)
        }
    }

    fun onSectionSelectingToggled() {
        selection.value = selection.value.toggled()
    }

    fun onSectionPointPicked(index: Int) {
        selection.value = selection.value.picked(index)
    }

    /** After a section went to Maps the mode ends. */
    fun onSectionSent() {
        selection.value = SectionSelection()
    }

    fun onEventHandled() {
        events.value = null
    }
}

/** Stable identity of a saved route: the position, not the name. */
fun Destination.routeId(): String = "dest:${position.lat},${position.lon}"
