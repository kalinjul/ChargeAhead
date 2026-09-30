package org.julakali.chargeahead.shared.ui.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.CarDiagnosticsRepository
import org.julakali.chargeahead.shared.domain.SoCDiagnostics
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
import org.julakali.chargeahead.shared.ui.WhileUiSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CarSoCUiState(
    val currentPercent: Double? = null,
    /** What the vehicle reported on the last attempt; `null` when it reported nothing. */
    val carReading: String? = null,
    /** The level was stored; the screen leaves. */
    val saved: Boolean = false,
) {
    /** Steps of ten from the top down, down to the reserve. */
    val steps: List<Int> get() = SOC_STEPS
}

private val SOC_STEPS = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10)

/** Entering the state of charge by hand while driving, in steps from high to low. */
class CarSoCViewModel(
    vehicles: VehicleRepository,
    diagnostics: CarDiagnosticsRepository,
    private val updateManualSoc: UpdateManualSocInteractor,
) : ViewModel() {

    private val saved = MutableStateFlow(false)

    val uiState: StateFlow<CarSoCUiState> = combine(
        vehicles.manualSocPercent,
        diagnostics.socDiagnostics,
        saved,
    ) { percent, diagnostics, saved ->
        CarSoCUiState(
            currentPercent = percent,
            carReading = diagnostics
                ?.takeIf { it.outcome == SoCDiagnostics.Outcome.AVAILABLE }
                ?.let { it.detail.orEmpty() },
            saved = saved,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, CarSoCUiState())

    fun onStepPicked(percent: Int) {
        viewModelScope.launch {
            updateManualSoc(UpdateManualSocInteractor.Params(percent.toDouble()))
            saved.value = true
        }
    }
}
