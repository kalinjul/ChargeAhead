package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import org.julakali.chargeahead.shared.domain.DEFAULT_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.MAX_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.usecases.GarageObserver
import org.julakali.chargeahead.shared.domain.usecases.SelectVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateArrivalSocInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The driver's cars and the arrival level the selected one plans with. */
data class GarageUiState(
    val vehicles: List<VehicleProfile> = emptyList(),
    val selected: VehicleProfile? = null,
    /** How full the battery should still be at the destination. */
    val arrivalSocPercent: Double = DEFAULT_ARRIVAL_SOC_PERCENT,
    val arrivalSheet: Int? = null,
    val fullRangeKm: Map<String, Double> = emptyMap(),
)

/** The garage screen: choose a car, set the arrival level. */
class GarageViewModel(
    private val vehicles: VehicleRepository,
    private val observeGarage: GarageObserver,
    private val selectVehicle: SelectVehicleInteractor,
    private val updateArrivalSoc: UpdateArrivalSocInteractor,
) : ViewModel() {

    private val arrivalSheet = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<GarageUiState> = combine(
        observeGarage.flow,
        vehicles.arrivalSocPercent,
        arrivalSheet,
    ) { garage, arrivalSoc, arrivalSheet ->
        GarageUiState(
            vehicles = garage.vehicles,
            selected = garage.selected,
            arrivalSocPercent = arrivalSoc,
            arrivalSheet = arrivalSheet,
            fullRangeKm = garage.fullRangeKm,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, GarageUiState())

    init {
        observeGarage(GarageObserver.Params())
    }

    /** Selecting also stores an edited profile. */
    fun onVehicleSelected(profile: VehicleProfile) {
        viewModelScope.launch { selectVehicle(SelectVehicleInteractor.Params(profile)) }
    }

    fun onArrivalSheetOpened() {
        viewModelScope.launch { arrivalSheet.value = vehicles.arrivalSocPercent.first().roundToInt() }
    }

    fun onArrivalSheetChanged(percent: Int) {
        // Only while the sheet is open: a late drag event must not reopen it.
        arrivalSheet.update { open -> open?.let { percent.coerceIn(ARRIVAL_SOC_RANGE) } }
    }

    fun onArrivalSheetDismissed() {
        arrivalSheet.value = null
    }

    fun onArrivalSheetConfirmed() {
        val chosen = arrivalSheet.value ?: return
        arrivalSheet.value = null
        viewModelScope.launch { updateArrivalSoc(UpdateArrivalSocInteractor.Params(chosen.toDouble())) }
    }
}

/** What the planner can honour as an arrival level — see [MAX_ARRIVAL_SOC_PERCENT]. */
val ARRIVAL_SOC_RANGE = 0..MAX_ARRIVAL_SOC_PERCENT.toInt()
