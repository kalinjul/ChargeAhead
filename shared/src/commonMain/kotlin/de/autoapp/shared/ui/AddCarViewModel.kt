package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.VehicleCatalog
import de.autoapp.shared.domain.VehiclePreset
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
    private val settings: SettingsStore,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<AddCarUiState> = combine(
        query,
        settings.vehicles,
    ) { query, owned ->
        val ownedNames = owned.mapTo(mutableSetOf()) { it.displayName }
        AddCarUiState(
            query = query,
            matches = VehicleCatalog.all.filter {
                it.name !in ownedNames && it.name.contains(query.trim(), ignoreCase = true)
            },
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, AddCarUiState())

    fun onQueryChanged(query: String) {
        this.query.value = query
    }

    /** Adds the preset and selects it — a car added is the car being driven. */
    fun onPresetAdded(preset: VehiclePreset) {
        viewModelScope.launch { settings.setVehicle(preset.toProfile()) }
    }
}
