package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.SavedRoute
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.usecases.SaveRouteInteractor
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
    private val saveRoute: SaveRouteInteractor,
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

    /** Turns a recent destination into a favourite, without a summary. */
    fun onFavourited(destination: Destination) {
        viewModelScope.launch { saveRoute(SaveRouteInteractor.Params(destination)) }
    }
}
