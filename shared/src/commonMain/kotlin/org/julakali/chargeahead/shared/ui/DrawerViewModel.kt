package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeMode
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.usecases.SetChargeModeInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateChargeFiltersInteractor
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
    /** AC mode, Stöbermodus or neither; the two buttons read their state off it. */
    val mode: ChargeMode = ChargeMode.NORMAL,
)

/** The navigation drawer's own state holder, since it is reachable from several screens. */
class DrawerViewModel(
    vehicles: VehicleRepository,
    preferences: PreferencesRepository,
    private val updateChargeFilters: UpdateChargeFiltersInteractor,
    private val setChargeMode: SetChargeModeInteractor,
) : ViewModel() {

    val uiState: StateFlow<DrawerUiState> = combine(
        vehicles.vehicle,
        preferences.networks,
        preferences.chargeFilters,
    ) { vehicle, networks, filters ->
        DrawerUiState(
            vehicleName = vehicle?.displayName,
            preferredNetworkCount = networks.selectedCount(),
            filters = filters,
            mode = ChargeMode.of(filters, networks),
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, DrawerUiState())

    fun onFiltersChanged(filters: ChargeFilters) {
        viewModelScope.launch { updateChargeFilters(UpdateChargeFiltersInteractor.Params(filters)) }
    }

    /** A mode button was tapped; [target] is [ChargeMode.NORMAL] when it was the one already on. */
    fun onModeSelected(target: ChargeMode) {
        viewModelScope.launch { setChargeMode(SetChargeModeInteractor.Params(target)) }
    }
}
