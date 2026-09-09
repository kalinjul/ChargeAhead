package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.PlanningFeature
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Place
import de.autoapp.shared.domain.SettingsStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/** Entering a destination and the charge level to start from. */
data class PlanSheetUiState(
    val query: String = "",
    /** `null` means the search itself failed — that is not the same as "nothing found". */
    val results: List<Place>? = emptyList(),
    val searching: Boolean = false,
    /** Picked from the results or the history; only then can planning start. */
    val chosen: Destination? = null,
    val recent: List<Destination> = emptyList(),
    val vehicleName: String? = null,
    val socInput: String = "",
    val from: LatLon? = null,
) {
    /** `null` while the typed value is not a usable percentage. */
    val socPercent: Int? get() = socInput.toIntOrNull()?.takeIf { it in 1..100 }

    val canPlan: Boolean get() = chosen != null && socPercent != null && vehicleName != null

    /** Too short to search on: the sheet offers the recent destinations instead. */
    val isQueryTooShort: Boolean get() = query.trim().length < MIN_QUERY_LENGTH

    companion object {
        const val MIN_QUERY_LENGTH = 3
    }
}

/**
 * The destination search behind the plan sheet.
 *
 * The debounce is not a nicety: Nominatim allows one request per second
 * (ROADMAP, open point 6), and a request per keystroke blows through that
 * inside a single word.
 */
@OptIn(FlowPreview::class)
class PlanSheetViewModel(
    private val feature: ChargeStopsFeature,
    settings: SettingsStore,
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
            socInput = input.socInput
                ?: (storedSoc ?: PlanningFeature.DEFAULT_ASSUMED_SOC_PERCENT).roundToInt().toString(),
            from = fix?.position,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, PlanSheetUiState())

    init {
        input
            // The pick travels with the query: choosing a destination writes
            // its name into the field, and that must not start a search for
            // what the driver just selected.
            .map { SearchInput(it.query.trim(), it.chosen != null) }
            .distinctUntilChanged()
            .onEach { search ->
                // Set before the debounce, so the spinner appears while
                // typing rather than only once the request goes out.
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
                // null result = the search failed; the sheet says that
                // instead of pretending nothing matched.
                val results = runCatching { feature.searchDestinations(search.query) }.getOrNull()
                input.update { it.copy(results = results, searching = false) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * The sheet was opened. Resets the entry — a destination typed two drives
     * ago must not sit in the field with the plan button already enabled.
     *
     * [destination] pre-fills it instead, which is what re-planning an open
     * trip needs: same target, different charge level or different filters.
     * It arrives as a pick rather than as typed text, so no search fires for
     * a destination that is already decided.
     */
    fun onSheetOpened(destination: Destination? = null) {
        input.value = destination
            ?.let { InputState(query = it.name, chosen = it) }
            ?: InputState()
    }

    fun onQueryChanged(query: String) {
        // Typing again discards the pick: the text no longer describes it.
        input.update { it.copy(query = query, chosen = null) }
    }

    fun onDestinationChosen(destination: Destination) {
        input.update {
            it.copy(chosen = destination, query = destination.name, results = emptyList(), searching = false)
        }
    }

    fun onSocChanged(socInput: String) {
        input.update { it.copy(socInput = socInput.filter(Char::isDigit).take(3)) }
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
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 600L
    }
}
