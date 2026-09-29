package org.julakali.chargeahead.shared.ui.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.ui.WhileUiSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CarHomeUiState(
    /** Where the committed trip goes; `null` without one. */
    val activeDestination: Destination? = null,
)

/** The car's start screen: enter a destination, charge right now, or open the active route. */
class CarHomeViewModel(
    trips: TripRepository,
) : ViewModel() {

    val uiState: StateFlow<CarHomeUiState> = trips.state
        .map { CarHomeUiState(activeDestination = it.committed?.plan?.destination) }
        .stateIn(viewModelScope, WhileUiSubscribed, CarHomeUiState())
}
