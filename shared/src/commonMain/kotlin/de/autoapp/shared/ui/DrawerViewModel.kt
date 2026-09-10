package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The navigation drawer: what is set, and the filters that can be set from there. */
data class DrawerUiState(
    val vehicleName: String? = null,
    val activeTariffCount: Int = 0,
    /** How many networks are picked; `0` means: no network filter. */
    val preferredNetworkCount: Int = 0,
    val filters: ChargeFilters = ChargeFilters(),
) {
    /** Something is narrowing the results — the map and the trip screen badge this. */
    val filtersCustomized: Boolean get() = !filters.isDefault || preferredNetworkCount > 0
}

/**
 * The drawer is not a screen but it carries state of its own — and it is
 * reachable from more than one screen, so it gets its own holder rather than
 * borrowing one screen's.
 */
class DrawerViewModel(
    private val settings: SettingsStore,
) : ViewModel() {

    val uiState: StateFlow<DrawerUiState> = combine(
        settings.vehicle,
        settings.activeTariffIds,
        settings.networks,
        settings.chargeFilters,
    ) { vehicle, tariffs, networks, filters ->
        DrawerUiState(
            vehicleName = vehicle?.displayName,
            activeTariffCount = tariffs.size,
            // Count what the picker can actually tick and the fetch actually
            // filters by — resolved catalog networks — not the raw stored keys,
            // which may still hold keys from an older catalog that resolve to
            // nothing and would inflate the number.
            preferredNetworkCount = networks.selectedNetworks().size,
            filters = filters,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, DrawerUiState())

    fun onFiltersChanged(filters: ChargeFilters) {
        viewModelScope.launch { settings.setChargeFilters(filters) }
    }
}
