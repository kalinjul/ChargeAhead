package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The navigation drawer: what is set, and the filters that can be set from there. */
data class DrawerUiState(
    val vehicleName: String? = null,
    /** How many networks are picked; `0` means: no network filter. */
    val preferredNetworkCount: Int = 0,
    val filters: ChargeFilters = ChargeFilters(),
)

/** The navigation drawer's own state holder, since it is reachable from several screens. */
class DrawerViewModel(
    private val settings: SettingsStore,
    networkRepository: NetworkRepository,
) : ViewModel() {

    val uiState: StateFlow<DrawerUiState> = combine(
        settings.vehicle,
        settings.networks,
        networkRepository.networks,
        settings.chargeFilters,
    ) { vehicle, networks, known, filters ->
        DrawerUiState(
            vehicleName = vehicle?.displayName,
            preferredNetworkCount = networks.selectedCount(known),
            filters = filters,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, DrawerUiState())

    fun onFiltersChanged(filters: ChargeFilters) {
        viewModelScope.launch { settings.setChargeFilters(filters) }
    }
}
