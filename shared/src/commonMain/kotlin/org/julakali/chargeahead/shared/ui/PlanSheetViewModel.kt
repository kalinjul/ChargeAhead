package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.PlanningFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.toDestination
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Entering a destination and the charge level to start from. */
data class PlanSheetUiState(
    val query: String = "",
    /** `null` means the search itself failed. */
    val results: List<Place>? = emptyList(),
    val searching: Boolean = false,
    /** Picked from the results or the history; only then can planning start. */
    val chosen: Destination? = null,
    val recent: List<Destination> = emptyList(),
    val vehicleName: String? = null,
    val socInput: String = "",
    /** The charge-level dialog's entry; `null` while it is closed. */
    val socEditorInput: String? = null,
    val from: LatLon? = null,
) {
    /** `null` while the entered value is not a usable percentage. */
    val socPercent: Int? get() = socInput.toIntOrNull()?.takeIf { it in SOC_PERCENT_RANGE }

    val canPlan: Boolean get() = chosen != null && socPercent != null && vehicleName != null

    /** Too short to search on. */
    val isQueryTooShort: Boolean get() = query.trim().length < MIN_QUERY_LENGTH

    companion object {
        const val MIN_QUERY_LENGTH = 3
    }
}

/** The charge levels the planner accepts. */
internal val SOC_PERCENT_RANGE = 1..100

/** The sheet always shows a charge level: the stored one, or the assumption. */
private fun Double?.asSocInput(): String =
    (this ?: PlanningFeature.DEFAULT_ASSUMED_SOC_PERCENT).roundToInt().toString()

/**
 * The destination search behind the plan sheet. Debounced, since Nominatim
 * allows one request per second.
 */
@OptIn(FlowPreview::class)
class PlanSheetViewModel(
    private val feature: ChargeStopsFeature,
    private val settings: SettingsStore,
) : ViewModel() {

    private val input = MutableStateFlow(InputState())

    val uiState: StateFlow<PlanSheetUiState> = combine(
        input,
        settings.recentDestinations,
        settings.vehicle,
        settings.manualSocPercent,
        feature.currentFix,
    ) { input, recent, vehicle, storedSoc, fix ->
        PlanSheetUiState(
            query = input.query,
            results = input.results,
            searching = input.searching,
            chosen = input.chosen,
            recent = recent,
            vehicleName = vehicle?.displayName,
            socInput = input.socInput ?: storedSoc.asSocInput(),
            socEditorInput = input.socEditor,
            from = fix?.position,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, PlanSheetUiState())

    init {
        input
            // Choosing a destination writes its name into the field without searching.
            .map { SearchInput(it.query.trim(), it.chosen != null) }
            .distinctUntilChanged()
            .onEach { search ->
                // Set before the debounce, so the spinner appears while typing.
                input.update { it.copy(searching = search.isSearchable) }
            }
            .debounce(SEARCH_DEBOUNCE_MILLIS)
            .onEach { search ->
                if (!search.isSearchable) {
                    input.update {
                        it.copy(results = if (search.chosen) it.results else emptyList(), searching = false)
                    }
                    return@onEach
                }
                // null result = the search failed.
                val results = runCatching { feature.searchDestinations(search.query) }.getOrNull()
                input.update { it.copy(results = results, searching = false) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * The sheet was opened. Resets the entry, or pre-fills it with
     * [destination] as a pick.
     */
    fun onSheetOpened(destination: Destination? = null) {
        input.value = destination
            ?.let { InputState(query = ChargeStopFormatter.label(it), chosen = it) }
            ?: InputState()
    }

    fun onQueryChanged(query: String) {
        // Typing again discards the pick.
        input.update { it.copy(query = query, chosen = null) }
    }

    fun onDestinationChosen(destination: Destination) {
        choose(destination, query = ChargeStopFormatter.label(destination))
    }

    fun onPlaceChosen(place: Place) {
        choose(place.toDestination(), query = ChargeStopFormatter.label(place))
    }

    private fun choose(destination: Destination, query: String) {
        input.update { it.copy(chosen = destination, query = query, results = emptyList(), searching = false) }
    }

    /** Opens the charge-level dialog on what the sheet currently shows. */
    fun onSocEditRequested() {
        viewModelScope.launch {
            val current = input.value.socInput ?: settings.manualSocPercent.first().asSocInput()
            input.update { it.copy(socEditor = current) }
        }
    }

    fun onSocInputChanged(socInput: String) {
        // Only while the dialog is open: a stray keystroke must not reopen it.
        input.update { state ->
            state.copy(socEditor = state.socEditor?.let { socInput.filter(Char::isDigit).take(3) })
        }
    }

    fun onSocEditDismissed() {
        input.update { it.copy(socEditor = null) }
    }

    /** Takes the entered level over into the sheet. Planning stays on the button. */
    fun onSocConfirmed() {
        val entered = input.value.socEditor?.toIntOrNull()?.takeIf { it in SOC_PERCENT_RANGE } ?: return
        input.update { it.copy(socInput = entered.toString(), socEditor = null) }
    }

    private data class SearchInput(val query: String, val chosen: Boolean) {
        val isSearchable: Boolean get() = !chosen && query.length >= PlanSheetUiState.MIN_QUERY_LENGTH
    }

    private data class InputState(
        val query: String = "",
        val results: List<Place>? = emptyList(),
        val searching: Boolean = false,
        val chosen: Destination? = null,
        /** `null` = untouched, so the stored charge level still shows through. */
        val socInput: String? = null,
        /** `null` = the charge-level dialog is closed. */
        val socEditor: String? = null,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 600L
    }
}
