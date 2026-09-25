package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.components.AppSlider
import org.julakali.chargeahead.android.phone.components.Fineprint
import org.julakali.chargeahead.android.phone.components.KeyValueGrid
import org.julakali.chargeahead.android.phone.components.SectionLabel
import org.julakali.chargeahead.android.phone.components.SocEditDialog
import org.julakali.chargeahead.android.phone.components.TickRow
import org.julakali.chargeahead.android.phone.components.TickStyle
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.ui.ARRIVAL_SOC_RANGE
import org.julakali.chargeahead.shared.ui.GarageUiState
import org.julakali.chargeahead.shared.ui.GarageViewModel
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/**
 * The garage: pick, add, remove vehicles; adjust consumption and charge level
 * for the selected one.
 */
@Composable
fun GarageRoute(
    onOpenAdvanced: () -> Unit,
    onOpenAdd: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarageViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GarageScreen(
        uiState = uiState,
        onSelect = viewModel::onVehicleSelected,
        onRemove = viewModel::onVehicleRemoved,
        onSocChange = viewModel::onSocChanged,
        onArrivalSocEdit = viewModel::onArrivalSocEditRequested,
        onArrivalSocInputChange = viewModel::onArrivalSocInputChanged,
        onArrivalSocConfirm = viewModel::onArrivalSocConfirmed,
        onArrivalSocDismiss = viewModel::onArrivalSocEditDismissed,
        onOpenAdvanced = onOpenAdvanced,
        onOpenAdd = onOpenAdd,
        modifier = modifier,
    )
}

@Composable
fun GarageScreen(
    uiState: GarageUiState,
    onSelect: (VehicleProfile) -> Unit,
    onRemove: (String) -> Unit,
    onSocChange: (Double) -> Unit,
    onArrivalSocEdit: () -> Unit,
    onArrivalSocInputChange: (String) -> Unit,
    onArrivalSocConfirm: () -> Unit,
    onArrivalSocDismiss: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicles = uiState.vehicles
    val selected = uiState.selected
    var deleteMode by remember { mutableStateOf(false) }

    // Arrival level editor.
    uiState.arrivalSocInput?.let { input ->
        SocEditDialog(
            value = input,
            title = stringResource(R.string.garage_arrival_title),
            confirmLabel = stringResource(R.string.soc_dialog_apply),
            onValueChange = onArrivalSocInputChange,
            onConfirm = onArrivalSocConfirm,
            onDismiss = onArrivalSocDismiss,
            valueRange = ARRIVAL_SOC_RANGE.first.toFloat()..ARRIVAL_SOC_RANGE.last.toFloat(),
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                SectionLabel(stringResource(R.string.garage_your_cars))
                AppCard {
                    vehicles.forEachIndexed { index, vehicle ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        TickRow(
                            label = vehicle.displayName,
                            checked = vehicle.displayName == selected?.displayName,
                            tick = if (deleteMode) TickStyle.DELETE else TickStyle.CHECK,
                            onClick = { if (deleteMode) onRemove(vehicle.displayName) else onSelect(vehicle) },
                            contentDescription = if (deleteMode) stringResource(R.string.garage_remove_one, vehicle.displayName) else null,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        if (deleteMode) {
                            TextButton(onClick = { deleteMode = false }) { Text(stringResource(R.string.garage_delete_done)) }
                        } else {
                            TextButton(onClick = onOpenAdd) { Text(stringResource(R.string.garage_add)) }
                            Spacer(Modifier.weight(1f))
                            if (vehicles.isNotEmpty()) {
                                TextButton(onClick = { deleteMode = true }) {
                                    Text(stringResource(R.string.garage_delete), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (selected == null) {
            item { Fineprint(stringResource(R.string.garage_no_car_yet)) }
        } else {
            item {
                SelectedVehiclePanel(
                    vehicle = selected,
                    fullRangeKm = uiState.selectedFullRangeKm,
                    presetConsumption = uiState.selectedPresetConsumption,
                    socPercent = uiState.socPercent,
                    socFromCar = uiState.socFromCar,
                    arrivalSocPercent = uiState.arrivalSocPercent,
                    onSelect = onSelect,
                    onSocChange = onSocChange,
                    onArrivalSocEdit = onArrivalSocEdit,
                    onOpenAdvanced = onOpenAdvanced,
                )
            }
        }
    }
}

@Composable
private fun SelectedVehiclePanel(
    vehicle: VehicleProfile,
    fullRangeKm: Double?,
    presetConsumption: Double?,
    socPercent: Double?,
    socFromCar: Boolean,
    arrivalSocPercent: Double,
    onSelect: (VehicleProfile) -> Unit,
    onSocChange: (Double) -> Unit,
    onArrivalSocEdit: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel(stringResource(R.string.garage_specs))
        KeyValueGrid(
            listOf(
                stringResource(R.string.garage_battery) to stringResource(R.string.garage_battery_value, vehicle.usableBatteryKwh.oneDecimal()),
                stringResource(R.string.garage_dc) to (
                    vehicle.dcPeakPowerKw?.let { stringResource(R.string.garage_dc_value, it.roundToInt()) }
                        ?: stringResource(R.string.garage_dc_unknown)
                    ),
                stringResource(R.string.garage_connector) to stringResource(R.string.garage_connector_ccs),
                stringResource(R.string.garage_range) to stringResource(
                    R.string.garage_range_value,
                    fullRangeKm?.roundToInt() ?: 0,
                ),
            ),
        )

        // Each slider keeps its drag state inside its own card.
        ConsumptionCard(vehicle = vehicle, presetConsumption = presetConsumption, onSelect = onSelect)
        SocCard(socPercent = socPercent, socFromCar = socFromCar, onSocChange = onSocChange)
        ArrivalSocCard(percent = arrivalSocPercent, onEdit = onArrivalSocEdit)

        TextButton(onClick = onOpenAdvanced) {
            Text(stringResource(R.string.garage_advanced))
        }
    }
}

@Composable
private fun ConsumptionCard(vehicle: VehicleProfile, presetConsumption: Double?, onSelect: (VehicleProfile) -> Unit) {
    // Slider commits on release.
    var consumption by remember(vehicle.displayName) {
        mutableStateOf(vehicle.consumptionKwhPer100Km.toFloat())
    }
    AppCard {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(
                stringResource(R.string.garage_consumption, consumption.toDouble().oneDecimal()),
                style = MaterialTheme.typography.titleSmall.tabular,
            )
            AppSlider(
                // Snap to half a kWh.
                value = consumption,
                onValueChange = { consumption = (it * 2).roundToInt() / 2f },
                onValueChangeFinished = {
                    onSelect(vehicle.copy(consumptionKwhPer100Km = consumption.toDouble()))
                },
                valueRange = 12f..30f,
            )
            presetConsumption?.let { spec ->
                Fineprint(stringResource(R.string.garage_consumption_hint, spec.oneDecimal()))
            }
        }
    }
}

@Composable
private fun SocCard(socPercent: Double?, socFromCar: Boolean, onSocChange: (Double) -> Unit) {
    var soc by remember(socPercent == null) { mutableStateOf((socPercent ?: 80.0).toFloat()) }
    AppCard {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(
                if (socPercent == null && !socFromCar) {
                    stringResource(R.string.garage_soc_unset)
                } else {
                    stringResource(R.string.garage_soc, soc.roundToInt())
                },
                style = MaterialTheme.typography.titleSmall.tabular,
            )
            AppSlider(
                value = soc,
                onValueChange = { soc = it.roundToInt().toFloat() },
                onValueChangeFinished = { onSocChange(soc.toDouble()) },
                valueRange = 0f..100f,
                enabled = !socFromCar,
            )
        }
    }
}

/** How full the battery should still be at the destination. Tapping opens the charge-level dialog. */
@Composable
private fun ArrivalSocCard(percent: Double, onEdit: () -> Unit) {
    AppCard(onClick = onEdit) {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(
                stringResource(R.string.garage_arrival_soc, percent.roundToInt()),
                style = MaterialTheme.typography.titleSmall.tabular,
            )
            Fineprint(stringResource(R.string.garage_arrival_hint))
        }
    }
}
