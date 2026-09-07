package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.KeyValueGrid
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.android.phone.components.TickRow
import de.autoapp.android.phone.components.TickStyle
import de.autoapp.android.phone.theme.tabular
import de.autoapp.shared.core.RangeCalculator
import de.autoapp.shared.domain.VehicleCatalog
import de.autoapp.shared.domain.VehicleProfile
import kotlin.math.roundToInt

/**
 * The garage: pick, add, remove vehicles; adjust consumption and charge level
 * for the selected one. Presets come from [VehicleCatalog]; consumption is a
 * slider because the spec-sheet value is a starting point, not the truth —
 * every driver is different.
 */
@Composable
fun GarageScreen(
    vehicles: List<VehicleProfile>,
    selected: VehicleProfile?,
    socPercent: Double?,
    socFromCar: Boolean,
    onSelect: (VehicleProfile) -> Unit,
    onRemove: (String) -> Unit,
    onSocChange: (Double) -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteMode by remember { mutableStateOf(false) }

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
            item { SelectedVehiclePanel(selected, socPercent, socFromCar, onSelect, onSocChange, onOpenAdvanced) }
        }
    }
}

@Composable
private fun SelectedVehiclePanel(
    vehicle: VehicleProfile,
    socPercent: Double?,
    socFromCar: Boolean,
    onSelect: (VehicleProfile) -> Unit,
    onSocChange: (Double) -> Unit,
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
                    RangeCalculator.rangeKm(vehicle, socPercent = 100.0, reserveSocPercent = 0.0).roundToInt(),
                ),
            ),
        )

        // Slider commits on release, not on every pixel: each commit rewrites
        // the garage entry and would otherwise spam the settings store.
        var consumption by remember(vehicle.displayName) {
            mutableStateOf(vehicle.consumptionKwhPer100Km.toFloat())
        }
        AppCard {
            Column(modifier = Modifier.padding(13.dp)) {
                Text(
                    stringResource(R.string.garage_consumption, consumption.toDouble().oneDecimal()),
                    style = MaterialTheme.typography.titleSmall.tabular,
                )
                Slider(
                    value = consumption,
                    onValueChange = { consumption = it },
                    onValueChangeFinished = {
                        onSelect(vehicle.copy(consumptionKwhPer100Km = consumption.toDouble()))
                    },
                    valueRange = 12f..30f,
                )
                presetConsumptionFor(vehicle.displayName)?.let { spec ->
                    Fineprint(stringResource(R.string.garage_consumption_hint, spec.oneDecimal()))
                }
            }
        }

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
                Slider(
                    value = soc,
                    onValueChange = { soc = it },
                    onValueChangeFinished = { onSocChange(soc.toDouble()) },
                    valueRange = 0f..100f,
                    enabled = !socFromCar,
                )
            }
        }

        TextButton(onClick = onOpenAdvanced) {
            Text(stringResource(R.string.garage_advanced))
        }
    }
}

private fun presetConsumptionFor(displayName: String): Double? =
    VehicleCatalog.all.firstOrNull { it.name == displayName }?.consumptionKwhPer100Km

internal fun Double.oneDecimal(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
