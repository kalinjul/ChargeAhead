package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.reportedByCar
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripStore
import org.julakali.chargeahead.shared.domain.invoke
import org.julakali.chargeahead.shared.domain.usecases.CommitTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.EndTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.PlanTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
import kotlin.math.roundToInt

data class CommittedTripUiState(
    val trip: CommittedTrip?,
    val selection: SectionSelection = SectionSelection(),
    val planning: Boolean = false,
    val startPosition: LatLon? = null,
    /** The charge-level prompt before a re-plan; `null` while closed. */
    val socInput: String? = null,
) {
    /** Re-planning needs to know where we are. */
    val canReplan: Boolean get() = trip != null && startPosition != null

    /** What "An Maps senden" hands over: the picked section, or the whole trip. */
    val mapsUrl: String? get() = trip?.plan?.mapsUrl(startPosition, selection)
}

sealed interface CommittedTripEvent {
    data object Replanned : CommittedTripEvent
    data object NoRoute : CommittedTripEvent
    data object Ended : CommittedTripEvent
}

/** The trip the driver is on: its stops, sending it again, planning it anew, ending it. */
class CommittedTripViewModel(
    private val settings: SettingsStore,
    private val feature: ChargeStopsFeature,
    private val planTrip: PlanTripInteractor,
    private val commitTrip: CommitTripInteractor,
    private val endTrip: EndTripInteractor,
    private val updateManualSoc: UpdateManualSocInteractor,
    private val tripStore: TripStore,
) : ViewModel() {

    private val selection = MutableStateFlow(SectionSelection())
    private val planning = MutableStateFlow(false)
    private val socEditor = MutableStateFlow<String?>(null)
    private val events = MutableStateFlow<CommittedTripEvent?>(null)

    val event: StateFlow<CommittedTripEvent?> = events.asStateFlow()

    val uiState: StateFlow<CommittedTripUiState> = combine(
        settings.committedTrip,
        selection,
        planning,
        feature.currentFix,
        socEditor,
    ) { trip, sectionSelection, isPlanning, fix, socInput ->
        CommittedTripUiState(trip, sectionSelection, isPlanning, fix?.position, socInput)
    }.stateIn(viewModelScope, WhileUiSubscribed, CommittedTripUiState(trip = null))

    /**
     * "Neu planen": with the car reporting its charge, plan right away;
     * otherwise ask for the level first, seeded with the stored one.
     */
    fun onReplanRequested() {
        if (feature.currentEnergy.value.reportedByCar) {
            replan()
            return
        }
        viewModelScope.launch {
            socEditor.value = settings.manualSocPercent.first()?.roundToInt()?.toString().orEmpty()
        }
    }

    fun onSocInputChanged(input: String) {
        // Only while the editor is open: a stray keystroke must not reopen it.
        socEditor.update { open -> open?.let { input.filter(Char::isDigit).take(3) } }
    }

    fun onSocEditDismissed() {
        socEditor.value = null
    }

    /** Re-plans from the level just entered, which is kept as the stored one. */
    fun onSocConfirmed() {
        val socPercent = socEditor.value?.toIntOrNull()?.takeIf { it in 1..100 } ?: return
        socEditor.value = null
        replan(socPercent.toDouble())
    }

    /** Plans the same destination from where we are now and keeps that instead of the stored plan. */
    fun replan(socPercent: Double? = null) {
        val trip = uiState.value.trip ?: return
        val from = feature.currentFix.value?.position ?: return
        selection.value = SectionSelection()
        viewModelScope.launch {
            planning.value = true
            try {
                socPercent?.let { updateManualSoc(UpdateManualSocInteractor.Params(it)) }
                val result = planTrip(PlanTripInteractor.Params(from, trip.plan.destination, startSocPercent = socPercent)).getOrNull()
                if (result is TripPlanResult.Planned) {
                    // The planner took the stored level (the car's last reading, or what was typed).
                    val plannedWith = socPercent ?: settings.manualSocPercent.first()
                    commitTrip(CommitTripInteractor.Params(result.plan, plannedWith))
                    // The plan is the committed one now, not something to look at on the home screen.
                    tripStore.clear()
                    events.value = CommittedTripEvent.Replanned
                } else {
                    events.value = CommittedTripEvent.NoRoute
                }
            } finally {
                planning.value = false
            }
        }
    }

    fun endTrip() {
        selection.value = SectionSelection()
        viewModelScope.launch {
            endTrip.invoke()
            events.value = CommittedTripEvent.Ended
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
