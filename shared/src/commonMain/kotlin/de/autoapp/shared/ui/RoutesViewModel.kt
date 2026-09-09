package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.SavedRoute
import de.autoapp.shared.domain.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Favourites on top, recently driven destinations below. */
data class RoutesUiState(
    val saved: List<SavedRoute> = emptyList(),
    val recent: List<Destination> = emptyList(),
)

/** The saved-routes sheet: rename, delete, and make a recent destination a favourite. */
class RoutesViewModel(
    private val settings: SettingsStore,
) : ViewModel() {

    val uiState: StateFlow<RoutesUiState> = combine(
        settings.savedRoutes,
        settings.recentDestinations,
    ) { saved, recent ->
        RoutesUiState(saved = saved, recent = recent)
    }.stateIn(viewModelScope, WhileUiSubscribed, RoutesUiState())

    fun onRenamed(route: SavedRoute, name: String) {
        viewModelScope.launch { settings.renameSavedRoute(route.id, name) }
    }

    fun onDeleted(route: SavedRoute) {
        viewModelScope.launch { settings.removeSavedRoute(route.id) }
    }

    /** Turns a recent destination into a favourite. Without a summary — there is no plan yet. */
    fun onFavourited(destination: Destination) {
        viewModelScope.launch {
            settings.saveRoute(
                SavedRoute(
                    id = destination.routeId(),
                    name = destination.name,
                    destination = destination,
                ),
            )
        }
    }
}
