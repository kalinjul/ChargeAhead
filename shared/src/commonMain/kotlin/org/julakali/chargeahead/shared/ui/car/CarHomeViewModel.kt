package org.julakali.chargeahead.shared.ui.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.ui.WhileUiSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CarHomeUiState(
    /** Where the committed trip goes; `null` without one. */
    val activeDestination: Destination? = null,
    /** The charge level the tiles show, car reading or manual entry; `null` when neither exists. */
    val socPercent: Int? = null,
)

/** The car's start screen: enter a destination, charge right now, or open the active route. */
class CarHomeViewModel(
    feature: ChargeStopsFeature,
    trips: TripRepository,
) : ViewModel() {

    val uiState: StateFlow<CarHomeUiState> = combine(trips.state, feature.currentEnergy) { trip, energy ->
        CarHomeUiState(
            activeDestination = trip.committed?.plan?.destination,
            socPercent = energy?.socPercent?.toInt(),
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, CarHomeUiState())
}
