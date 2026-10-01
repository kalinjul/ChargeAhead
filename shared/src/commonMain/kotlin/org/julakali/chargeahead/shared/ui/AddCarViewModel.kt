package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.domain.usecases.SelectVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.VehiclePresetsObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val observePresets: VehiclePresetsObserver,
    private val selectVehicle: SelectVehicleInteractor,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<AddCarUiState> = combine(query, observePresets.flow) { query, matches ->
        AddCarUiState(query = query, matches = matches)
    }.stateIn(viewModelScope, WhileUiSubscribed, AddCarUiState())

    init {
        observePresets(VehiclePresetsObserver.Params(query = ""))
    }

    fun onQueryChanged(query: String) {
        this.query.value = query
        observePresets(VehiclePresetsObserver.Params(query))
    }

    /** Adds the preset and selects it. */
    fun onPresetAdded(preset: VehiclePreset) {
        viewModelScope.launch { selectVehicle(SelectVehicleInteractor.Params(preset.toProfile())) }
    }
}
