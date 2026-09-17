package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkCatalog
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.OperatorKey
import org.julakali.chargeahead.shared.domain.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Choosing charging networks. */
data class NetworksUiState(
    /** The catalog rows to show, filtered by [search]. */
    val networks: List<Network> = emptyList(),
    /** The ticked networks (catalog keys), edits included. */
    val selected: Set<String> = emptySet(),
    /** [NetworkPreferences.onlyPreferred]: whether the selection filters at all. */
    val onlyPreferred: Boolean = NetworkPreferences().onlyPreferred,
    val search: String = "",
)

/**
 * The network picker. Edits are staged and committed after a pause or on
 * [onLeave], since every write re-runs planning and the map query.
 */
class NetworksViewModel(
    private val settings: SettingsStore,
) : ViewModel() {

    private val search = MutableStateFlow("")

    /** The edited preferences — `null` while this visit has changed nothing. */
    private val staged = MutableStateFlow<NetworkPreferences?>(null)

    /**
     * The order to draw the pills in, frozen so a tap doesn't move the pill.
     * `null` until [onEnter] snapshots it.
     */
    private val displayOrder = MutableStateFlow<List<String>?>(null)

    /** The pending debounced commit, restarted on every edit. */
    private var commitJob: Job? = null

    // Each catalog name folded once, up front.
    private val foldedCatalog: List<Pair<Network, String>> =
        NetworkCatalog.all.map { it to OperatorKey.folded(it.name) }
    private val foldedByKey: Map<String, String> =
        foldedCatalog.associate { (network, folded) -> network.key to folded }

    /** The catalog rows that survive the search, in catalog order. */
    private val matches: Flow<List<Network>> = search
        .map { it.trim() }
        .distinctUntilChanged()
        .map { query ->
            if (query.isEmpty()) {
                NetworkCatalog.all
            } else {
                val needle = OperatorKey.folded(query)
                foldedCatalog.filter { (_, folded) -> folded.contains(needle) }.map { it.first }
            }
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<NetworksUiState> = combine(
        matches,
        settings.networks,
        staged,
        search,
        displayOrder,
    ) { matches, stored, staged, search, order ->
        val edited = staged ?: stored
        NetworksUiState(
            networks = order.orderFor(matches) ?: matches.committedFirst(stored),
            selected = edited.preferredOperators,
            onlyPreferred = edited.onlyPreferred,
            search = search,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, NetworksUiState())

    fun onSearchChanged(query: String) {
        val cleared = search.value.isNotBlank() && query.isBlank()
        search.value = query
        // Clearing the field rebuilds the list anyway, so re-sort here.
        if (cleared) refreshOrder()
    }

    fun onNetworkToggled(key: String) = edit { preferences ->
        val selected = preferences.preferredOperators
        preferences.copy(
            preferredOperators = if (key in selected) selected - key else selected + key,
        )
    }

    fun onOnlyPreferredChanged(enabled: Boolean) = edit { it.copy(onlyPreferred = enabled) }

    /** The screen was opened: snapshot the order from what is committed. */
    fun onEnter() {
        displayOrder.value = null
        refreshOrder()
    }

    /** Re-freeze the order from the selection as it stands, edits included. */
    private fun refreshOrder() {
        viewModelScope.launch {
            displayOrder.value = (staged.value ?: settings.networks.first()).orderedKeys()
        }
    }

    /** The screen is being left: commit at once. */
    fun onLeave() {
        commitJob?.cancel()
        viewModelScope.launch { commit() }
    }

    private fun edit(block: (NetworkPreferences) -> NetworkPreferences) {
        viewModelScope.launch {
            staged.value = block(staged.value ?: settings.networks.first())
            scheduleCommit()
        }
    }

    private fun scheduleCommit() {
        commitJob?.cancel()
        commitJob = viewModelScope.launch {
            delay(COMMIT_DEBOUNCE_MS)
            commit()
        }
    }

    private suspend fun commit() {
        // Read inside the coroutine, so the last queued edit is in.
        val edited = staged.value ?: return
        settings.setNetworks(edited)
        // The ViewModel outlives the screen.
        staged.value = null
    }

    /** The frozen order applied to whatever [matches] survived the search. */
    private fun List<String>?.orderFor(matches: List<Network>): List<Network>? {
        val order = this ?: return null
        val index = order.withIndex().associate { (i, key) -> key to i }
        return matches.sortedBy { index[it.key] ?: Int.MAX_VALUE }
    }

    /** Committed picks first, then alphabetical. */
    private fun List<Network>.committedFirst(committed: NetworkPreferences): List<Network> =
        sortedWith(
            compareByDescending<Network> { it.key in committed.preferredOperators }
                .thenBy { foldedByKey[it.key] ?: it.name },
        )

    private fun NetworkPreferences.orderedKeys(): List<String> =
        NetworkCatalog.all.committedFirst(this).map { it.key }

    private companion object {
        const val COMMIT_DEBOUNCE_MS = 5_000L
    }
}
