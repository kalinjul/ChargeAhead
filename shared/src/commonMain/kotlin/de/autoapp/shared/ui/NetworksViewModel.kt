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
    /** The catalog rows to show; filtered by [search] when non-empty, full list otherwise. */
    val networks: List<Network> = emptyList(),
    /** Staged selection (catalog keys) — not yet written to settings. */
    val pending: Set<String> = emptySet(),
    /** The persisted [NetworkPreferences.preferredOperators]. */
    val committed: Set<String> = emptySet(),
    /** True when [pending] differs from [committed] and Confirm would do something. */
    val canConfirm: Boolean = false,
    val search: String = "",
)

/** The network picker — catalog-backed, staged confirm. */
class NetworksViewModel(
    private val settings: SettingsStore,
) : ViewModel() {

    private val search = MutableStateFlow("")
    private val pending = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<NetworksUiState> = combine(
        settings.networks,
        pending,
        search,
    ) { prefs, pending, search ->
        val committed = prefs.preferredOperators
        val filtered = if (search.isNotBlank()) {
            val needle = OperatorKey.folded(search.trim())
            NetworkCatalog.all.filter { OperatorKey.folded(it.name).contains(needle) }
        } else {
            NetworkCatalog.all
        }
        NetworksUiState(
            networks = filtered,
            pending = pending,
            committed = committed,
            canConfirm = pending != committed,
            search = search,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, NetworksUiState())

    init {
        viewModelScope.launch {
            pending.value = settings.networks.first().preferredOperators
        }
    }

    fun onSearchChanged(query: String) {
        search.value = query
    }

    fun onNetworkToggled(key: String) {
        pending.value = if (key in pending.value) pending.value - key else pending.value + key
    }

    fun onConfirm() {
        val snapshot = pending.value
        viewModelScope.launch {
            settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = snapshot))
        }
    }
}
