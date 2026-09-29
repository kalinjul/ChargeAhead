package org.julakali.chargeahead.shared.ui.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.usecases.DestinationSearchObserver
import org.julakali.chargeahead.shared.ui.WhileUiSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CarDestinationSearchUiState(
    val searching: Boolean = false,
    /** Offered while nothing is typed. */
    val recents: List<Destination> = emptyList(),
    val places: List<Place> = emptyList(),
    /** Text is typed but not submitted yet, so an empty list means "not searched yet". */
    val awaitingSubmit: Boolean = false,
)

/** Typing a destination in the car; geocoding fires on submit only, not per keystroke. */
class CarDestinationSearchViewModel(
    settings: SettingsStore,
    private val observeDestinationSearch: DestinationSearchObserver,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val submittedQuery = MutableStateFlow("")

    val uiState: StateFlow<CarDestinationSearchUiState> = combine(
        query,
        submittedQuery,
        settings.recentDestinations,
        observeDestinationSearch.flow,
    ) { query, submitted, recents, search ->
        val blank = query.isBlank()
        CarDestinationSearchUiState(
            searching = search.searching,
            recents = if (blank) recents else emptyList(),
            places = if (blank) emptyList() else search.results.orEmpty(),
            awaitingSubmit = !blank && query != submitted,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, CarDestinationSearchUiState())

    init {
        observeDestinationSearch(DestinationSearchObserver.Params(query = ""))
    }

    fun onSearchTextChanged(text: String) {
        query.value = text
        if (text.isBlank()) observeDestinationSearch(DestinationSearchObserver.Params(query = ""))
    }

    fun onSearchSubmitted(text: String) {
        query.value = text
        if (text.isBlank()) return
        submittedQuery.value = text
        observeDestinationSearch(DestinationSearchObserver.Params(text, debounce = false))
    }
}
