package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlanTrip
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.ToggleSavedRoute
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripStore
import org.julakali.chargeahead.shared.domain.UpdateArrivalSoc
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
    private val planTrip: PlanTrip,
    private val updateArrivalSoc: UpdateArrivalSoc,
    private val toggleSavedRoute: ToggleSavedRoute,
    private val tripStore: TripStore,
    private val settings: SettingsStore,
) : ViewModel() {

    private val isPlanning = combine(planTrip.inProgress, updateArrivalSoc.inProgress) { plan, replan -> plan || replan }
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
        combine(tripStore.plan, isPlanning, selection, socEditor, arrivalSocEditor, ::PlanInputs),
        settings.savedRoutes,
        settings.manualSocPercent,
        feature.currentFix,
    ) { inputs, saved, socPercent, fix ->
        val plan = inputs.plan
        when {
            inputs.planning -> TripUiState.Planning
            plan == null -> TripUiState.NoPlan
            else -> TripUiState.Planned(
                plan = plan,
                isSaved = saved.any { it.destination.position == plan.destination.position },
                startPosition = fix?.position,
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
        val from = feature.currentFix.value?.position ?: return
        // A new plan starts with a clean selection and closed editors.
        selection.value = SectionSelection()
        socEditor.value = null
        arrivalSocEditor.value = null
        viewModelScope.launch {
            // Persisted alongside, so the plan starts at once.
            launch { socPercent?.let { settings.setManualSocPercent(it) } }
            planTrip(PlanTrip.Params(from, destination, startSocPercent = socPercent))
                .onSuccess(::onPlanned)
                .onFailure { events.value = TripEvent.NoRoute }
        }
    }

    /** The driver dismissed the trip; the map goes back to browsing. */
    fun clear() {
        selection.value = SectionSelection()
        socEditor.value = null
        arrivalSocEditor.value = null
        tripStore.clear()
    }

    private fun onPlanned(result: TripPlanResult) {
        events.value = when (result) {
            is TripPlanResult.Planned -> TripEvent.PlanReady
            is TripPlanResult.NoVehicle -> TripEvent.VehicleMissing
            is TripPlanResult.NoChargerInReach -> TripEvent.NoChargerInReach(result.afterKm)
            is TripPlanResult.NoRoute -> TripEvent.NoRoute
        }
    }

    /**
     * Saves the current plan's destination as a favourite, or removes it
     * again. [summary] comes from the caller's resources.
     */
    fun toggleSaved(summary: String) {
        val current = tripStore.plan.value ?: return
        viewModelScope.launch {
            toggleSavedRoute(ToggleSavedRoute.Params(current.destination, summary)).onSuccess { saved ->
                events.value = if (saved) TripEvent.RouteSaved else TripEvent.RouteRemoved
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
        val destination = tripStore.plan.value?.destination ?: return
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

    /** Stores the arrival level just entered; the trip is re-planned with it. */
    fun onArrivalSocConfirmed() {
        val socPercent = arrivalSocEditor.value?.toIntOrNull()?.takeIf { it in ARRIVAL_SOC_RANGE } ?: return
        val from = feature.currentFix.value?.position ?: return
        arrivalSocEditor.value = null
        viewModelScope.launch {
            updateArrivalSoc(UpdateArrivalSoc.Params(socPercent.toDouble(), from))
                .onSuccess { result -> result?.let(::onPlanned) }
                .onFailure { events.value = TripEvent.NoRoute }
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
