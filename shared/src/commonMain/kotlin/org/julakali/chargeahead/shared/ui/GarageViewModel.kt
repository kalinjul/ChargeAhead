package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import org.julakali.chargeahead.shared.core.RangeCalculator
import org.julakali.chargeahead.shared.domain.DEFAULT_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.MAX_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.VehicleCatalog
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.usecases.RemoveVehicleInteractor
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
    /** The arrival-level dialog's entry; `null` while it is closed. */
    val arrivalSocInput: String? = null,
    /** The selected car's range on a full battery, down to 0 %. */
    val selectedFullRangeKm: Double? = null,
    /** The catalog consumption, when the selected car was added from a preset. */
    val selectedPresetConsumption: Double? = null,
)

/** The garage screen: choose, edit, remove a car. */
class GarageViewModel(
    private val vehicles: VehicleRepository,
    private val selectVehicle: SelectVehicleInteractor,
    private val removeVehicle: RemoveVehicleInteractor,
    private val updateArrivalSoc: UpdateArrivalSocInteractor,
) : ViewModel() {

    private val arrivalSocEditor = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GarageUiState> = combine(
        vehicles.vehicles,
        vehicles.vehicle,
        vehicles.arrivalSocPercent,
        arrivalSocEditor,
    ) { vehicles, selected, arrivalSoc, arrivalEditor ->
        GarageUiState(
            vehicles = vehicles,
            selected = selected,
            arrivalSocPercent = arrivalSoc,
            arrivalSocInput = arrivalEditor,
            selectedFullRangeKm = selected?.let { RangeCalculator.rangeKm(it, socPercent = 100.0, reserveSocPercent = 0.0) },
            selectedPresetConsumption = selected?.let { VehicleCatalog.presetFor(it.displayName)?.consumptionKwhPer100Km },
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, GarageUiState())

    /** Selecting also stores an edited profile. */
    fun onVehicleSelected(profile: VehicleProfile) {
        viewModelScope.launch { selectVehicle(SelectVehicleInteractor.Params(profile)) }
    }

    fun onVehicleRemoved(displayName: String) {
        viewModelScope.launch { removeVehicle(RemoveVehicleInteractor.Params(displayName)) }
    }

    /** Opens the arrival-level dialog on the level currently in force. */
    fun onArrivalSocEditRequested() {
        viewModelScope.launch {
            arrivalSocEditor.value = vehicles.arrivalSocPercent.first().roundToInt().toString()
        }
    }

    fun onArrivalSocInputChanged(input: String) {
        // Only while the dialog is open: a stray keystroke must not reopen it.
        arrivalSocEditor.update { open -> open?.let { input.filter(Char::isDigit).take(3) } }
    }

    fun onArrivalSocEditDismissed() {
        arrivalSocEditor.value = null
    }

    fun onArrivalSocConfirmed() {
        val entered = arrivalSocEditor.value?.toIntOrNull()?.takeIf { it in ARRIVAL_SOC_RANGE } ?: return
        arrivalSocEditor.value = null
        viewModelScope.launch { updateArrivalSoc(UpdateArrivalSocInteractor.Params(entered.toDouble())) }
    }
}

/** What the planner can honour as an arrival level — see [MAX_ARRIVAL_SOC_PERCENT]. */
val ARRIVAL_SOC_RANGE = 0..MAX_ARRIVAL_SOC_PERCENT.toInt()
