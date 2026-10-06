package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.combine
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.usecases.PlanTripInteractor
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.usecases.CommitTripInteractor
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.mapsUrl
import org.julakali.chargeahead.shared.domain.SectionSelection
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.reportedByCar
import org.julakali.chargeahead.shared.domain.SOC_RANGE
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.UnreachableTrip
import org.julakali.chargeahead.shared.domain.invoke
import org.julakali.chargeahead.shared.domain.usecases.DismissPlannedTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.ReplanWithArrivalSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
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

    /** What the trip sheet shows: a plan, or why there is none, with both levels editable either way. */
    sealed interface Sheet : TripUiState {
        val destination: Destination
        val startSocPercent: Double?

        /** The quick charge-level editor's input; `null` while it is closed. */
        val socInput: String?

        /** The arrival-level editor's input; `null` while it is closed. */
        val arrivalSocInput: String?

        /** The start-level editor opened because "Neu planen" had no car reading to go on. */
        val socAskedForReplan: Boolean
    }

    data class Planned(
        val plan: TripPlan,
        val startPosition: LatLon?,
        override val startSocPercent: Double?,
        val selection: SectionSelection = SectionSelection(),
        override val socInput: String? = null,
        override val arrivalSocInput: String? = null,
        override val socAskedForReplan: Boolean = false,
    ) : Sheet {
        override val destination: Destination get() = plan.destination

        /** What "An Maps senden" hands over: the picked section, or the whole trip. */
        val mapsUrl: String get() = plan.mapsUrl(startPosition, selection)
    }

    data class Unreachable(
        override val destination: Destination,
        val why: UnreachableTrip,
        override val startSocPercent: Double?,
        override val socInput: String? = null,
        override val arrivalSocInput: String? = null,
        override val socAskedForReplan: Boolean = false,
    ) : Sheet
}

/**
 * What just happened, once. The UI turns it into a snackbar or a screen
 * change and then calls [TripViewModel.onEventHandled].
 */
sealed interface TripEvent {

    /** The sheet has something to show: a plan, or why there is none. */
    data object SheetReady : TripEvent

    data object VehicleMissing : TripEvent

    /** Planning failed outside the planner's own outcomes, e.g. the backend was unreachable. */
    data object NoConnection : TripEvent

    /** The plan went to Maps and is the committed trip now. */
    data object TripCommitted : TripEvent
}

/** Planning a trip and everything the result screen shows. */
class TripViewModel(
    private val feature: ChargeStopsFeature,
    private val planTrip: PlanTripInteractor,
    private val replanWithArrivalSoc: ReplanWithArrivalSocInteractor,
    private val commitTrip: CommitTripInteractor,
    private val updateManualSoc: UpdateManualSocInteractor,
    private val dismissPlannedTrip: DismissPlannedTripInteractor,
    private val trips: TripRepository,
    private val vehicles: VehicleRepository,
) : ViewModel() {

    private val isPlanning = combine(planTrip.inProgress, replanWithArrivalSoc.inProgress) { plan, replan -> plan || replan }
    private val selection = MutableStateFlow(SectionSelection())
    private val socEditor = MutableStateFlow<String?>(null)
    private val socAskedForReplan = MutableStateFlow(false)
    private val arrivalSocEditor = MutableStateFlow<String?>(null)
    private val events = MutableStateFlow<TripEvent?>(null)

    val event: StateFlow<TripEvent?> = events.asStateFlow()

    val uiState: StateFlow<TripUiState> = combine(
        trips.state,
        isPlanning,
        selection,
        socEditor,
        arrivalSocEditor,
        vehicles.manualSocPercent,
        feature.currentFix,
        socAskedForReplan,
    ) { trip, planning, sectionSelection, socInput, arrivalSocInput, socPercent, fix, askedForReplan ->
        val plan = trip.planned
        val unreachable = trip.unreachable
        val destination = trip.destination
        when {
            planning -> TripUiState.Planning
            unreachable != null && destination != null -> TripUiState.Unreachable(
                destination = destination,
                why = unreachable,
                startSocPercent = socPercent,
                socInput = socInput,
                arrivalSocInput = arrivalSocInput,
                socAskedForReplan = askedForReplan,
            )
            plan == null -> TripUiState.NoPlan
            else -> TripUiState.Planned(
                plan = plan,
                startPosition = fix?.position,
                startSocPercent = socPercent,
                selection = sectionSelection,
                socInput = socInput,
                arrivalSocInput = arrivalSocInput,
                socAskedForReplan = askedForReplan,
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
        resetEditing()
        viewModelScope.launch {
            // Persisted alongside, so the plan starts at once.
            launch { socPercent?.let { updateManualSoc(UpdateManualSocInteractor.Params(it)) } }
            planTrip(PlanTripInteractor.Params(from, destination, startSocPercent = socPercent))
                .onSuccess(::onPlanned)
                .onFailure { events.value = TripEvent.NoConnection }
        }
    }

    /** The driver dismissed the trip; the map goes back to browsing. */
    fun clear() {
        resetEditing()
        viewModelScope.launch { dismissPlannedTrip() }
    }

    private fun resetEditing() {
        selection.value = SectionSelection()
        socEditor.value = null
        arrivalSocEditor.value = null
    }

    private fun onPlanned(result: TripPlanResult) {
        events.value = if (result == TripPlanResult.NoVehicle) TripEvent.VehicleMissing else TripEvent.SheetReady
    }

    /**
     * The plan just went to Maps: it becomes the committed trip and leaves the
     * home screen, which goes back to browsing.
     */
    fun commit() {
        val current = uiState.value as? TripUiState.Planned ?: return
        viewModelScope.launch {
            commitTrip(CommitTripInteractor.Params(current.plan, current.startSocPercent)).onSuccess {
                resetEditing()
                events.value = TripEvent.TripCommitted
            }
        }
    }

    /**
     * Opens the quick charge-level entry on the start row, seeded with the
     * level this plan was made from.
     */
    fun onStartSocEditRequested() {
        socAskedForReplan.value = false
        openStartSocEditor()
    }

    private fun openStartSocEditor() {
        viewModelScope.launch {
            socEditor.value = vehicles.manualSocPercent.first()?.roundToInt()?.toString().orEmpty()
        }
    }

    /**
     * "Neu planen": with the car reporting its charge, plan right away from
     * where we are; otherwise ask for the level first, and the confirm plans.
     */
    fun onReplanRequested() {
        val destination = trips.state.value.currentDestination ?: return
        if (feature.currentEnergy.value.reportedByCar) {
            plan(destination)
        } else {
            socAskedForReplan.value = true
            openStartSocEditor()
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
        val socPercent = socEditor.value?.toIntOrNull()?.takeIf { it in SOC_RANGE } ?: return
        val destination = trips.state.value.currentDestination ?: return
        socEditor.value = null
        plan(destination, socPercent.toDouble())
    }

    /** Opens the arrival-level editor on the level this plan was made with. */
    fun onArrivalSocEditRequested() {
        viewModelScope.launch {
            arrivalSocEditor.value = vehicles.arrivalSocPercent.first().roundToInt().toString()
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
            replanWithArrivalSoc(ReplanWithArrivalSocInteractor.Params(socPercent.toDouble(), from))
                .onSuccess { result -> result?.let(::onPlanned) }
                .onFailure { events.value = TripEvent.NoConnection }
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
