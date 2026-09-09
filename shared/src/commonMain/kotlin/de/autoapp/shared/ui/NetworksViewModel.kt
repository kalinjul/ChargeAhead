package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.ChargeStopsState
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.OperatorOption
import de.autoapp.shared.domain.OperatorOptions
import de.autoapp.shared.domain.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Choosing charging networks. */
data class NetworksUiState(
    val preferences: NetworkPreferences = NetworkPreferences(),
    /** Everything the surroundings offer, unfiltered — decides "empty" versus "still loading". */
    val available: List<OperatorOption> = emptyList(),
    /** The rows to show; `null` while the filter is still being applied. */
    val shown: List<OperatorOption>? = null,
    val search: String = "",
    /** The debounced search — what [shown] actually matches. */
    val query: String = "",
    /** No networks yet because no location or no sites yet, not because there are none. */
    val loading: Boolean = false,
)

/**
 * The network picker.
 *
 * The list comes from the charging sites in the current surroundings, not
 * from a maintained enumeration: it is never incomplete, and it only offers
 * what actually occurs here. In a well-covered area that is still a few
 * hundred entries, which is why filtering is debounced and runs off the main
 * thread — it folds and scans the whole list on every pass.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NetworksViewModel(
    private val settings: SettingsStore,
    feature: ChargeStopsFeature,
) : ViewModel() {

    private val search = MutableStateFlow("")

    // Clearing the field is exempt from the debounce: the full list is
    // already known, and waiting for it would feel broken.
    private val query = search.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }

    // Kept across passes: replacing it with null on every keystroke would
    // make the list flicker. It is only null before the very first pass.
    private val shown = MutableStateFlow<List<OperatorOption>?>(null)

    val uiState: StateFlow<NetworksUiState> = combine(
        settings.networks,
        feature.state,
        search,
        query,
        shown,
    ) { preferences, state, search, query, shown ->
        NetworksUiState(
            preferences = preferences,
            available = state.availableOperators,
            shown = shown,
            search = search,
            query = query,
            loading = state.phase == ChargeStopsState.Phase.WAITING_FOR_LOCATION ||
                state.phase == ChargeStopsState.Phase.LOADING,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, NetworksUiState())

    init {
        combine(
            feature.state.map { it.availableOperators }.distinctUntilChanged(),
            query,
        ) { available, query -> available to query }
            .mapLatest { (available, query) ->
                // The selection is read once per pass on purpose: reordering
                // while the driver is ticking would pull the row out from
                // under their finger.
                val selected = settings.networks.first().preferredOperators
                withContext(Dispatchers.Default) {
                    OperatorOptions.forPicker(available, query, selected)
                }
            }
            .onEach { shown.value = it }
            .launchIn(viewModelScope)
    }

    fun onSearchChanged(search: String) {
        this.search.value = search
    }

    fun onOnlyPreferredChanged(onlyPreferred: Boolean) {
        update { it.copy(onlyPreferred = onlyPreferred) }
    }

    fun onOperatorToggled(key: String) {
        update {
            it.copy(
                preferredOperators = if (key in it.preferredOperators) {
                    it.preferredOperators - key
                } else {
                    it.preferredOperators + key
                },
            )
        }
    }

    /**
     * "All" and "none" act on the **displayed** list: with "ionity" typed,
     * one tap selects every spelling variant of that network without
     * touching the rest of the selection. That is what makes the search more
     * than a reading aid.
     */
    fun onAllShownSelected() {
        val keys = uiState.value.shown.orEmpty().map { it.key }
        update { it.copy(preferredOperators = it.preferredOperators + keys) }
    }

    fun onNoShownSelected() {
        val keys = uiState.value.shown.orEmpty().mapTo(mutableSetOf()) { it.key }
        update { it.copy(preferredOperators = it.preferredOperators - keys) }
    }

    private fun update(change: (NetworkPreferences) -> NetworkPreferences) {
        viewModelScope.launch {
            settings.setNetworks(change(settings.networks.first()))
        }
    }

    private companion object {
        /**
         * Long enough that a fast typist filters once instead of per letter,
         * short enough that a finished word feels immediate.
         */
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}
