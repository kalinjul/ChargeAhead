package org.julakali.chargeahead.shared.ui.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.LiveConnectorGroup
import org.julakali.chargeahead.shared.domain.distanceKmTo
import org.julakali.chargeahead.shared.domain.usecases.LiveConnectorsObserver
import org.julakali.chargeahead.shared.ui.WhileUiSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CarSiteDetailUiState(
    /** Straight line from the current fix; `null` without one. */
    val distanceKm: Double? = null,
    /** Live charge points, `null` while the site has none or they were not fetched. */
    val live: List<LiveConnectorGroup>? = null,
)

/** One site in the car: how far it is from here, and what its charge points are doing. */
class CarSiteDetailViewModel(
    feature: ChargeStopsFeature,
    site: ChargeSite,
    liveConnectors: LiveConnectorsObserver,
) : ViewModel() {

    val uiState: StateFlow<CarSiteDetailUiState> = combine(feature.currentFix, liveConnectors.flow) { fix, live ->
        CarSiteDetailUiState(distanceKm = fix?.position?.distanceKmTo(site.position), live = live)
    }.stateIn(viewModelScope, WhileUiSubscribed, CarSiteDetailUiState())

    init {
        liveConnectors(LiveConnectorsObserver.Params(site.liveStatusId))
    }
}
