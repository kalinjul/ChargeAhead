package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.Tariff
import de.autoapp.shared.domain.TariffCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which tariffs the driver holds — every price comparison quotes against these. */
data class SubscriptionsUiState(
    val query: String = "",
    val matches: List<Tariff> = emptyList(),
    val activeIds: Set<String> = emptySet(),
)

/** The subscriptions screen: search the catalog, tick what the driver holds. */
class SubscriptionsViewModel(
    private val settings: SettingsStore,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SubscriptionsUiState> = combine(
        query,
        settings.activeTariffIds,
    ) { query, activeIds ->
        SubscriptionsUiState(
            query = query,
            matches = TariffCatalog.all.filter { it.displayName.contains(query.trim(), ignoreCase = true) },
            activeIds = activeIds,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, SubscriptionsUiState())

    fun onQueryChanged(query: String) {
        this.query.value = query
    }

    fun onTariffToggled(id: String) {
        viewModelScope.launch {
            val active = settings.activeTariffIds.first()
            settings.setActiveTariffIds(if (id in active) active - id else active + id)
        }
    }
}
