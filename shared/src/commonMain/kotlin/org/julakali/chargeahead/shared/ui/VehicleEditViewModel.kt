package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.julakali.chargeahead.shared.domain.VehicleCatalogRepository
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.domain.differsFrom
import org.julakali.chargeahead.shared.domain.presetOf
import org.julakali.chargeahead.shared.domain.usecases.EditVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.EditVehicleInteractor.Edit
import org.julakali.chargeahead.shared.domain.usecases.RemoveVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.RestoreCatalogValuesInteractor
import kotlin.math.abs
import kotlin.math.round
import org.julakali.chargeahead.shared.formatDecimal

sealed interface VehicleEditor {
    val applicable: Boolean

    data class Name(val input: String) : VehicleEditor {
        override val applicable: Boolean get() = input.isNotBlank()
    }

    data class Battery(val input: String) : VehicleEditor {
        override val applicable: Boolean get() = input.toPositiveDoubleOrNull() != null
    }

    data class DcPeak(val input: String) : VehicleEditor {
        override val applicable: Boolean get() = input.toPositiveDoubleOrNull() != null
    }

    data class Consumption(val input: String) : VehicleEditor {
        override val applicable: Boolean get() = input.toPositiveDoubleOrNull() != null
    }
}

data class VehicleEditUiState(
    val vehicle: VehicleProfile? = null,
    val catalog: VehiclePreset? = null,
    /** The name aside: it is the driver's, not the catalog's. */
    val canRestoreCatalogValues: Boolean = false,
    val editor: VehicleEditor? = null,
    val confirmingRemoval: Boolean = false,
    val removed: Boolean = false,
)

class VehicleEditViewModel(
    vehicles: VehicleRepository,
    catalog: VehicleCatalogRepository,
    private val editVehicle: EditVehicleInteractor,
    private val restoreCatalogValues: RestoreCatalogValuesInteractor,
    private val removeVehicle: RemoveVehicleInteractor,
) : ViewModel() {

    private val editor = MutableStateFlow<VehicleEditor?>(null)
    private val confirmingRemoval = MutableStateFlow(false)
    private val removed = MutableStateFlow(false)

    val uiState: StateFlow<VehicleEditUiState> = combine(
        vehicles.vehicle,
        catalog.presets,
        editor,
        combine(confirmingRemoval, removed, ::Pair),
    ) { vehicle, presets, editor, (confirmingRemoval, removed) ->
        val preset = vehicle?.let { presets.presetOf(it) }
        VehicleEditUiState(
            vehicle = vehicle,
            catalog = preset,
            canRestoreCatalogValues = vehicle != null && preset != null && vehicle.differsFrom(preset),
            editor = editor,
            confirmingRemoval = confirmingRemoval,
            removed = removed,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, VehicleEditUiState())

    fun onNameEditOpened() = open { VehicleEditor.Name(it.displayName) }

    fun onBatteryEditOpened() = open { VehicleEditor.Battery(it.usableBatteryKwh.asLocalInput()) }

    fun onDcPeakEditOpened() = open { VehicleEditor.DcPeak(it.dcPeakPowerKw?.asLocalInput().orEmpty()) }

    fun onConsumptionEditOpened() = open { VehicleEditor.Consumption(it.consumptionKwhPer100Km.asLocalInput()) }

    fun onEditorInputChanged(input: String) = editor.update {
        when (it) {
            is VehicleEditor.Name -> it.copy(input = input)
            is VehicleEditor.Battery -> it.copy(input = input)
            is VehicleEditor.DcPeak -> it.copy(input = input)
            is VehicleEditor.Consumption -> it.copy(input = input)
            null -> it
        }
    }

    fun onEditorConfirmed() {
        val open = editor.value?.takeIf { it.applicable } ?: return
        val edit = when (open) {
            is VehicleEditor.Name -> Edit.Name(open.input)
            is VehicleEditor.Battery -> Edit.Battery(open.input.toPositiveDoubleOrNull()!!)
            is VehicleEditor.DcPeak -> Edit.DcPeak(open.input.toPositiveDoubleOrNull()!!)
            is VehicleEditor.Consumption -> Edit.Consumption(open.input.toPositiveDoubleOrNull()!!)
        }
        editor.value = null
        viewModelScope.launch { editVehicle(EditVehicleInteractor.Params(edit)) }
    }

    fun onEditorDismissed() {
        editor.value = null
    }

    /** Drops the driver's changes; the car follows the catalog again. */
    fun onCatalogValuesRestored() {
        viewModelScope.launch { restoreCatalogValues(Unit) }
    }

    fun onVehicleRemoveRequested() {
        confirmingRemoval.value = uiState.value.vehicle != null
    }

    fun onVehicleRemoveCancelled() {
        confirmingRemoval.value = false
    }

    fun onVehicleRemoveConfirmed() {
        confirmingRemoval.value = false
        val id = uiState.value.vehicle?.id ?: return
        viewModelScope.launch {
            removeVehicle(RemoveVehicleInteractor.Params(id))
            removed.value = true
        }
    }

    private fun open(editorFor: (VehicleProfile) -> VehicleEditor) {
        editor.value = uiState.value.vehicle?.let(editorFor)
    }
}

/** Accepts the German decimal comma. */
internal fun String.toPositiveDoubleOrNull(): Double? =
    replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0.0 }

/** What an editor opens with: [asInput] in the UI language's notation ("16,5" / "16.5"). */
internal fun Double.asLocalInput(): String = asInput().let { if ('.' in it) formatDecimal(it.toDouble(), 1) else it }

/** One decimal at most, whole numbers without the ".0". */
internal fun Double.asInput(): String {
    val oneDecimal = round(this * 10) / 10
    val whole = round(oneDecimal)
    return if (abs(oneDecimal - whole) < 0.001) whole.toLong().toString() else oneDecimal.toString()
}
