package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.shared.core.RangeCalculator
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.VehicleCatalog
import de.autoapp.shared.domain.VehicleProfile
import kotlinx.coroutines.launch
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
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var deleteMode by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (adding) {
        AddVehicleList(
            owned = vehicles.map { it.displayName }.toSet(),
            onAdd = { preset ->
                onSelect(preset.toProfile())
                adding = false
                scope.launch { snackbar.showSnackbar(preset.name) }
            },
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                stringResource(R.string.garage_your_cars),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(vehicles, key = { it.displayName }) { vehicle ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(vehicle.displayName, modifier = Modifier.weight(1f))
                if (deleteMode) {
                    IconButton(onClick = { onRemove(vehicle.displayName) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_remove),
                            contentDescription = stringResource(R.string.garage_remove_one, vehicle.displayName),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    RadioButton(
                        selected = vehicle.displayName == selected?.displayName,
                        onClick = { onSelect(vehicle) },
                    )
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                if (deleteMode) {
                    TextButton(onClick = { deleteMode = false }) {
                        Text(stringResource(R.string.garage_delete_done))
                    }
                } else {
                    TextButton(onClick = { adding = true }) {
                        Text(stringResource(R.string.garage_add))
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        if (vehicles.isNotEmpty()) {
                            TextButton(onClick = { deleteMode = true }) {
                                Text(
                                    stringResource(R.string.garage_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        if (selected == null) {
            item {
                Text(
                    stringResource(R.string.garage_no_car_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
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
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text(stringResource(R.string.garage_specs), style = MaterialTheme.typography.titleSmall)
        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SpecRow(
                    stringResource(R.string.garage_battery),
                    stringResource(R.string.garage_battery_value, vehicle.usableBatteryKwh.oneDecimal()),
                )
                SpecRow(
                    stringResource(R.string.garage_dc),
                    vehicle.dcPeakPowerKw?.let { stringResource(R.string.garage_dc_value, it.roundToInt()) }
                        ?: stringResource(R.string.garage_dc_unknown),
                )
                SpecRow(
                    stringResource(R.string.garage_range),
                    stringResource(
                        R.string.garage_range_value,
                        RangeCalculator.rangeKm(vehicle, socPercent = 100.0, reserveSocPercent = 0.0).roundToInt(),
                    ),
                )
            }
        }

        // Slider commits on release, not on every pixel: each commit rewrites
        // the garage entry and would otherwise spam the settings store.
        var consumption by remember(vehicle.displayName) {
            mutableStateOf(vehicle.consumptionKwhPer100Km.toFloat())
        }
        Text(stringResource(R.string.garage_consumption, consumption.toDouble().oneDecimal()))
        Slider(
            value = consumption,
            onValueChange = { consumption = it },
            onValueChangeFinished = {
                onSelect(vehicle.copy(consumptionKwhPer100Km = consumption.toDouble()))
            },
            valueRange = 12f..30f,
        )
        presetConsumptionFor(vehicle.displayName)?.let { spec ->
            Text(
                stringResource(R.string.garage_consumption_hint, spec.oneDecimal()),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        var soc by remember(socPercent == null) { mutableStateOf((socPercent ?: 80.0).toFloat()) }
        Text(
            if (socPercent == null && !socFromCar) {
                stringResource(R.string.garage_soc_unset)
            } else {
                stringResource(R.string.garage_soc, soc.roundToInt())
            },
        )
        Slider(
            value = soc,
            onValueChange = { soc = it },
            onValueChangeFinished = { onSocChange(soc.toDouble()) },
            valueRange = 0f..100f,
            enabled = !socFromCar,
        )

        TextButton(onClick = onOpenAdvanced) {
            Text(stringResource(R.string.garage_advanced))
        }
    }
}

@Composable
private fun AddVehicleList(
    owned: Set<String>,
    onAdd: (de.autoapp.shared.domain.VehiclePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    val hits = VehicleCatalog.all.filter {
        it.name !in owned && it.name.contains(search.trim(), ignoreCase = true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(stringResource(R.string.garage_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        if (hits.isEmpty()) {
            Text(
                stringResource(R.string.garage_none_found),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn {
            items(hits, key = { it.name }) { preset ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { onAdd(preset) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(
                                    R.string.garage_preset_line,
                                    preset.usableBatteryKwh.oneDecimal(),
                                    preset.consumptionKwhPer100Km.oneDecimal(),
                                    preset.dcPeakPowerKw.roundToInt(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun presetConsumptionFor(displayName: String): Double? =
    VehicleCatalog.all.firstOrNull { it.name == displayName }?.consumptionKwhPer100Km

internal fun Double.oneDecimal(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
