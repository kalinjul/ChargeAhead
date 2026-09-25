package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.VehicleCatalog
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.domain.usecases.SelectVehicleInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Picking a car from the catalog. */
data class AddCarUiState(
    val query: String = "",
    /** Catalog minus what is already in the garage, filtered by [query]. */
    val matches: List<VehiclePreset> = emptyList(),
)

/** The add-car screen: search the catalog, tap to add. */
class AddCarViewModel(
    settings: SettingsStore,
    private val selectVehicle: SelectVehicleInteractor,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<AddCarUiState> = combine(
        query,
        settings.vehicles,
    ) { query, owned ->
        val ownedNames = owned.mapTo(HashSet()) { it.displayName }
        val needle = query.trim()
        AddCarUiState(
            query = query,
            matches = VehicleCatalog.all.filter {
                it.name !in ownedNames && it.name.contains(needle, ignoreCase = true)
            },
        )
        // Off the main thread.
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, WhileUiSubscribed, AddCarUiState())

    fun onQueryChanged(query: String) {
        this.query.value = query
    }

    /** Adds the preset and selects it. */
    fun onPresetAdded(preset: VehiclePreset) {
        viewModelScope.launch { selectVehicle(SelectVehicleInteractor.Params(preset.toProfile())) }
    }
}
