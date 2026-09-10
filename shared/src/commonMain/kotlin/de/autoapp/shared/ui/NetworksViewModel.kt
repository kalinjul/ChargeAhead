package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.NetworkCatalog
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.OperatorKey
import de.autoapp.shared.domain.SettingsStore
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

    val uiState: StateFlow<NetworksUiState> = combine(
        settings.networks,
        staged,
        search,
    ) { stored, staged, search ->
        val edited = staged ?: stored
        val matches = if (search.isNotBlank()) {
            val needle = OperatorKey.folded(search.trim())
            NetworkCatalog.all.filter { OperatorKey.folded(it.name).contains(needle) }
        } else {
            NetworkCatalog.all
        }
        // Selected networks float to the top so the driver's picks stay in
        // view; the rest is plain alphabetical. Selection follows the staged
        // edit, so a tick lifts its network straight away.
        val ordered = matches.sortedWith(
            compareByDescending<Network> { it.key in edited.preferredOperators }
                .thenBy { OperatorKey.folded(it.name) },
        )
        NetworksUiState(
            networks = ordered,
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
     * The screen is being left — this is where the edits take effect.
     *
     * Reading [staged] inside the coroutine, not before it: [edit] queues on
     * the same scope, so the last tick before the back gesture is in by the
     * time this runs.
     */
    fun onLeave() {
        viewModelScope.launch {
            val edited = staged.value ?: return@launch
            settings.setNetworks(edited)
            // Back to "unchanged": the ViewModel is activity-scoped and
            // outlives the screen, so the next visit must start from what
            // was just stored, not from this visit's staged copy.
            staged.value = null
        }
    }

    private fun edit(block: (NetworkPreferences) -> NetworkPreferences) {
        viewModelScope.launch {
            staged.value = block(staged.value ?: settings.networks.first())
        }
    }
}
