package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.CarDataPoint
import org.julakali.chargeahead.shared.domain.CarDiagnosticsRepository
import org.julakali.chargeahead.shared.domain.SoCDiagnostics
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.reportedByCar
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Everything the car hardware last delivered, and the charge level planning starts from. */
data class CarDataUiState(
    val points: List<CarDataPoint> = emptyList(),
    /** The stored level, shown only; the car or the planning dialogs set it. */
    val socPercent: Double? = null,
    val socFromCar: Boolean = false,
    val socDiagnostics: SoCDiagnostics? = null,
)

/** The read-only car-data debug view. */
class CarDataViewModel(
    diagnostics: CarDiagnosticsRepository,
    vehicles: VehicleRepository,
    feature: ChargeStopsFeature,
) : ViewModel() {

    val uiState: StateFlow<CarDataUiState> = combine(
        diagnostics.carDebugData,
        vehicles.manualSocPercent,
        feature.currentEnergy,
        diagnostics.socDiagnostics,
    ) { points, socPercent, energy, socDiagnostics ->
        CarDataUiState(points = points, socPercent = socPercent, socFromCar = energy.reportedByCar, socDiagnostics = socDiagnostics)
    }.stateIn(viewModelScope, WhileUiSubscribed, CarDataUiState())
}
