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
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.invoke
import org.julakali.chargeahead.shared.domain.usecases.DismissPlannedTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.ReplanWithArrivalSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        val startPosition: LatLon?,
        val startSocPercent: Double?,
        val selection: SectionSelection = SectionSelection(),
        /** The quick charge-level editor's input; `null` while it is closed. */
        val socInput: String? = null,
        /** The arrival-level editor's input; `null` while it is closed. */
        val arrivalSocInput: String? = null,
        /** The start-level editor opened because "Neu planen" had no car reading to go on. */
        val socAskedForReplan: Boolean = false,
    ) : TripUiState {
        /** What "An Maps senden" hands over: the picked section, or the whole trip. */
        val mapsUrl: String get() = plan.mapsUrl(startPosition, selection)
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
        trips.state.map { it.planned },
        isPlanning,
        selection,
        socEditor,
        arrivalSocEditor,
        vehicles.manualSocPercent,
        feature.currentFix,
        socAskedForReplan,
    ) { plan, planning, sectionSelection, socInput, arrivalSocInput, socPercent, fix, askedForReplan ->
        when {
            planning -> TripUiState.Planning
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
                .onFailure { events.value = TripEvent.NoRoute }
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
        events.value = when (result) {
            is TripPlanResult.Planned -> TripEvent.PlanReady
            is TripPlanResult.NoVehicle -> TripEvent.VehicleMissing
            is TripPlanResult.NoChargerInReach -> TripEvent.NoChargerInReach(result.afterKm)
            is TripPlanResult.NoRoute -> TripEvent.NoRoute
        }
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
        val destination = trips.state.value.planned?.destination ?: return
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
        val socPercent = socEditor.value?.toIntOrNull()?.takeIf { it in 1..100 } ?: return
        val destination = trips.state.value.planned?.destination ?: return
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
