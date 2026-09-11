package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.NetworkCatalog
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.OperatorKey
import de.autoapp.shared.domain.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Choosing charging networks. */
data class NetworksUiState(
    /**
     * The catalog rows to show; filtered by [search] when non-empty, full
     * list — the driver's own networks first — otherwise.
     */
    val networks: List<Network> = emptyList(),
    /** The ticked networks (catalog keys), edits included. */
    val selected: Set<String> = emptySet(),
    /** [NetworkPreferences.onlyPreferred]: whether the selection filters at all. */
    val onlyPreferred: Boolean = NetworkPreferences().onlyPreferred,
    val search: String = "",
)

/**
 * The network picker — catalog-backed, written back when the screen is left.
 *
 * Edits are staged rather than persisted per tap: every write re-runs the
 * planning and the map query, and ticking half a dozen networks in a row
 * would do that half a dozen times. [onLeave] is the commit; the back
 * gesture is the confirmation.
 */
class NetworksViewModel(
    private val settings: SettingsStore,
) : ViewModel() {

    private val search = MutableStateFlow("")

    /** The edited preferences — `null` while this visit has changed nothing. */
    private val staged = MutableStateFlow<NetworkPreferences?>(null)

    /**
     * The order to draw the pills in, frozen for the visit. Ticking a network
     * lifts it to the top *next* time — while the screen is open the list holds
     * still, so a tap doesn't yank the pill out from under the finger. `null`
     * until [onEnter] snapshots it; the ordering falls back to committed-first.
     */
    private val displayOrder = MutableStateFlow<List<String>?>(null)

    /** The pending debounced commit, restarted on every edit. */
    private var commitJob: Job? = null

    val uiState: StateFlow<NetworksUiState> = combine(
        settings.networks,
        staged,
        search,
        displayOrder,
    ) { stored, staged, search, order ->
        val edited = staged ?: stored
        val matches = if (search.isNotBlank()) {
            val needle = OperatorKey.folded(search.trim())
            NetworkCatalog.all.filter { OperatorKey.folded(it.name).contains(needle) }
        } else {
            NetworkCatalog.all
        }
        NetworksUiState(
            // Selection follows the staged edit — a tick colours in at once —
            // but the order follows the frozen snapshot, so the pill stays put.
            networks = order.orderFor(matches) ?: matches.committedFirst(stored),
            selected = edited.preferredOperators,
            onlyPreferred = edited.onlyPreferred,
            search = search,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, NetworksUiState())

    fun onSearchChanged(query: String) {
        search.value = query
    }

    fun onNetworkToggled(key: String) = edit { preferences ->
        val selected = preferences.preferredOperators
        preferences.copy(
            preferredOperators = if (key in selected) selected - key else selected + key,
        )
    }

    fun onOnlyPreferredChanged(enabled: Boolean) = edit { it.copy(onlyPreferred = enabled) }

    /**
     * The screen was opened — snapshot the order from what is committed, so the
     * driver's picks sit on top now and stay put until they leave and return.
     */
    fun onEnter() {
        displayOrder.value = null
        viewModelScope.launch { displayOrder.value = settings.networks.first().orderedKeys() }
    }

    /**
     * The screen is being left — commit at once, cancelling any pending
     * debounce. This covers the back arrow, the system back gesture and the
     * drawer alike.
     */
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

    /**
     * The map query and replan are expensive, so ticking a row only stages —
     * the fetch waits for a [COMMIT_DEBOUNCE_MS] pause in selecting, or for
     * the screen to be left. Selection itself stays instant either way.
     */
    private fun scheduleCommit() {
        commitJob?.cancel()
        commitJob = viewModelScope.launch {
            delay(COMMIT_DEBOUNCE_MS)
            commit()
        }
    }

    private suspend fun commit() {
        // Reading [staged] inside the coroutine, not before it: [edit] queues
        // on the same scope, so the last tick before a commit is in by now.
        val edited = staged.value ?: return
        settings.setNetworks(edited)
        // Back to "unchanged": the ViewModel is activity-scoped and outlives
        // the screen, so the next visit starts from what was just stored.
        staged.value = null
    }

    /** The frozen order applied to whatever [matches] survived the search. */
    private fun List<String>?.orderFor(matches: List<Network>): List<Network>? {
        val order = this ?: return null
        val index = order.withIndex().associate { (i, key) -> key to i }
        return matches.sortedBy { index[it.key] ?: Int.MAX_VALUE }
    }

    /** Committed picks first, then alphabetical — the order a fresh visit sees. */
    private fun List<Network>.committedFirst(committed: NetworkPreferences): List<Network> =
        sortedWith(
            compareByDescending<Network> { it.key in committed.preferredOperators }
                .thenBy { OperatorKey.folded(it.name) },
        )

    private fun NetworkPreferences.orderedKeys(): List<String> =
        NetworkCatalog.all.committedFirst(this).map { it.key }

    private companion object {
        const val COMMIT_DEBOUNCE_MS = 5_000L
    }
}
