package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.OperatorKey
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.usecases.UpdateNetworksInteractor
import org.julakali.chargeahead.shared.domain.usecases.SelectableNetworksObserver
import org.julakali.chargeahead.shared.domain.committedFirst
import org.julakali.chargeahead.shared.domain.SelectableNetwork
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    val search: String = "",
)

/**
 * The network picker. Edits are staged and committed after a pause or on
 * [onLeave], since every write re-runs planning and the map query.
 */
class NetworksViewModel(
    private val preferences: PreferencesRepository,
    private val observeSelectable: SelectableNetworksObserver,
    /** The same rows unsearched, for freezing an order over all of them. */
    private val observeAllSelectable: SelectableNetworksObserver,
    private val updateNetworks: UpdateNetworksInteractor,
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

    init {
        observeSelectable(SelectableNetworksObserver.Params(query = ""))
        observeAllSelectable(SelectableNetworksObserver.Params(query = ""))
    }

    val uiState: StateFlow<NetworksUiState> = combine(
        observeSelectable.flow,
        preferences.networks,
        staged,
        search,
        displayOrder,
    ) { matches, stored, staged, search, order ->
        val edited = staged ?: stored
        NetworksUiState(
            networks = order.orderFor(matches) ?: matches.committedFirst(stored),
            selected = edited.preferredOperators,
            search = search,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, NetworksUiState())

    fun onSearchChanged(query: String) {
        val cleared = search.value.isNotBlank() && query.isBlank()
        search.value = query
        observeSelectable(SelectableNetworksObserver.Params(query))
        // Clearing the field rebuilds the list anyway, so re-sort here.
        if (cleared) refreshOrder()
    }

    fun onNetworkToggled(key: String) = edit { preferences ->
        val selected = preferences.preferredOperators
        preferences.copy(
            preferredOperators = if (key in selected) selected - key else selected + key,
        )
    }

    /** The screen was opened: snapshot the order from what is committed. */
    fun onEnter() {
        displayOrder.value = null
        refreshOrder()
    }

    /** Re-freeze the order from the selection as it stands, edits included. */
    private fun refreshOrder() {
        viewModelScope.launch {
            // The frozen order covers the whole catalog, whatever the search shows of it.
            displayOrder.value = (staged.value ?: preferences.networks.first()).orderedKeys(observeAllSelectable.flow.first())
        }
    }

    /** The screen is being left: commit at once. */
    fun onLeave() {
        commitJob?.cancel()
        // Leaving pops the back-stack entry, which clears this ViewModel and
        // cancels viewModelScope, so the write must not be its child.
        viewModelScope.launch(NonCancellable) { commit() }
    }

    private fun edit(block: (NetworkPreferences) -> NetworkPreferences) {
        viewModelScope.launch {
            staged.value = block(staged.value ?: preferences.networks.first())
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
        // Only the ticks: the modes own onlyPreferred, and one may have been switched meanwhile.
        val stored = preferences.networks.first()
        updateNetworks(UpdateNetworksInteractor.Params(stored.copy(preferredOperators = edited.preferredOperators)))
        staged.value = null
    }

    /** The frozen order applied to whatever rows survived the search. */
    private fun List<String>?.orderFor(matches: List<SelectableNetwork>): List<Network>? {
        val order = this ?: return null
        val index = order.withIndex().associate { (i, key) -> key to i }
        return matches.map { it.network }.sortedBy { index[it.key] ?: Int.MAX_VALUE }
    }

    private fun NetworkPreferences.orderedKeys(catalog: List<SelectableNetwork>): List<String> =
        catalog.committedFirst(this).map { it.key }

    private companion object {
        const val COMMIT_DEBOUNCE_MS = 5_000L
    }
}
