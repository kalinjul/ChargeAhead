package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.ObserveDestinationSearch
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.distanceKmTo
import org.julakali.chargeahead.shared.toDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

/** One line of the results panel: a recent destination or a geocoder hit. */
data class SearchRow(
    val destination: Destination,
    val title: String,
    val detail: String?,
    /** Straight-line from the current fix; `null` without one. */
    val distanceKm: Double?,
    val recent: Boolean,
)

data class SearchUiState(
    val query: String = "",
    val rows: List<SearchRow> = emptyList(),
    val searching: Boolean = false,
    /** The geocoder itself failed. */
    val failed: Boolean = false,
    val hasVehicle: Boolean = false,
    /** Raw hits, for the iOS bridge. */
    val results: List<Place>? = emptyList(),
) {
    val isQueryTooShort: Boolean get() = query.trim().length < MIN_QUERY_LENGTH

    companion object {
        const val MIN_QUERY_LENGTH = ObserveDestinationSearch.MIN_QUERY_LENGTH
    }
}

/** Recents while the query is too short to search on, hits once it is. */
fun searchRows(query: String, results: List<Place>?, recent: List<Destination>, from: LatLon?): List<SearchRow> =
    if (query.trim().length < SearchUiState.MIN_QUERY_LENGTH) {
        recent.map { SearchRow(it, it.name, it.address, from?.distanceKmTo(it.position), recent = true) }
    } else {
        results.orEmpty().map { place ->
            val destination = place.toDestination()
            SearchRow(destination, place.name, destination.address, from?.distanceKmTo(place.position), recent = false)
        }
    }

/** "< 1", "4,2", "42" — the unit is the caller's resource string. */
fun Double.asKmLabel(): String = when {
    this < 1 -> "< 1"
    this < 10 -> {
        val tenths = (this * 10).roundToInt()
        "${tenths / 10},${tenths % 10}"
    }
    else -> roundToInt().toString()
}

class SearchViewModel(
    private val feature: ChargeStopsFeature,
    private val observeDestinationSearch: ObserveDestinationSearch,
    settings: SettingsStore,
) : ViewModel() {

    private val input = MutableStateFlow(Input())

    val uiState: StateFlow<SearchUiState> = combine(
        input,
        observeDestinationSearch.flow,
        settings.recentDestinations,
        settings.vehicle,
        feature.currentFix,
    ) { (query, pick), search, recent, vehicle, fix ->
        SearchUiState(
            query = query,
            rows = pick?.let { listOf(SearchRow(it, it.name, it.address, fix?.position?.distanceKmTo(it.position), recent = false)) }
                ?: searchRows(query, search.results, recent, fix?.position),
            searching = search.searching,
            failed = search.results == null,
            hasVehicle = vehicle != null,
            results = search.results,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, SearchUiState())

    init {
        search("")
    }

    /**
     * The bar took focus. A [prefill] (the current destination when re-planning)
     * sits in the field as a pick, not as a query: typing over it starts searching.
     */
    fun onOpened(prefill: Destination? = null) {
        input.value = Input(query = prefill?.let(ChargeStopFormatter::label) ?: "", pick = prefill)
        search("")
    }

    fun onQueryChanged(query: String) {
        input.value = Input(query)
        search(query)
    }

    fun onClosed() {
        onQueryChanged("")
    }

    private fun search(query: String) {
        observeDestinationSearch(ObserveDestinationSearch.Params(query.trim()))
    }

    private data class Input(val query: String = "", val pick: Destination? = null)
}
