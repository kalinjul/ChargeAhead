package de.autoapp.android.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.SoCDiagnostics
import de.autoapp.shared.domain.VehicleProfile

/**
 * Free-form entry of battery capacity and consumption — deliberately no
 * vehicle list (ARCHITECTURE.md, open point 4).
 *
 * Written immediately on every valid change, not only on "Save". A state of
 * charge the driver types in that fails to land because of a forgotten tap
 * would be the worst way for the reachability calculation to go wrong.
 */
@Composable
fun VehicleSettingsScreen(
    vehicle: VehicleProfile?,
    socPercent: Double?,
    socFromCar: Boolean,
    onVehicleChange: (VehicleProfile?) -> Unit,
    onSocChange: (Double?) -> Unit,
    diagnostics: SoCDiagnostics? = null,
    modifier: Modifier = Modifier,
) {
    var name by remember(vehicle) { mutableStateOf(vehicle?.displayName.orEmpty()) }
    var battery by remember(vehicle) { mutableStateOf(vehicle?.usableBatteryKwh?.asInput().orEmpty()) }
    var consumption by remember(vehicle) {
        mutableStateOf(vehicle?.consumptionKwhPer100Km?.asInput().orEmpty())
    }
    var connectors by remember(vehicle) {
        mutableStateOf(vehicle?.acceptedConnectors ?: emptySet())
    }
    var soc by remember(socPercent) { mutableStateOf(socPercent?.asInput().orEmpty()) }

    // Build a profile from the fields — or null as long as something is missing.
    fun publishVehicle(
        newName: String = name,
        newBattery: String = battery,
        newConsumption: String = consumption,
        newConnectors: Set<ConnectorType> = connectors,
    ) {
        val batteryKwh = newBattery.toPositiveDoubleOrNull()
        val consumptionKwh = newConsumption.toPositiveDoubleOrNull()
        onVehicleChange(
            if (batteryKwh == null || consumptionKwh == null) {
                null
            } else {
                VehicleProfile(newName.trim(), batteryKwh, consumptionKwh, newConnectors)
            },
        )
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.phone_settings_intro),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; publishVehicle(newName = it) },
            label = { Text(stringResource(R.string.phone_field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        NumberField(
            value = battery,
            onValueChange = { battery = it; publishVehicle(newBattery = it) },
            label = stringResource(R.string.phone_field_battery),
            isError = battery.isNotBlank() && battery.toPositiveDoubleOrNull() == null,
            errorText = stringResource(R.string.phone_error_positive_number),
        )

        NumberField(
            value = consumption,
            onValueChange = { consumption = it; publishVehicle(newConsumption = it) },
            label = stringResource(R.string.phone_field_consumption),
            isError = consumption.isNotBlank() && consumption.toPositiveDoubleOrNull() == null,
            errorText = stringResource(R.string.phone_error_positive_number),
        )

        Text(
            text = stringResource(R.string.phone_connectors_heading),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.phone_connectors_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        // UNKNOWN doesn't appear: "my car accepts unknown connectors" makes
        // no sense, and the planner treats unknown connectors leniently anyway.
        ConnectorType.entries.filter { it != ConnectorType.UNKNOWN }.forEach { type ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = type in connectors,
                    onCheckedChange = { checked ->
                        connectors = if (checked) connectors + type else connectors - type
                        publishVehicle(newConnectors = connectors)
                    },
                )
                Text(ChargeStopFormatter.connectorLabel(type))
            }
        }

        NumberField(
            value = soc,
            onValueChange = { input ->
                soc = input
                onSocChange(input.toPercentOrNull())
            },
            label = stringResource(R.string.phone_field_soc),
            isError = soc.isNotBlank() && soc.toPercentOrNull() == null,
            errorText = stringResource(R.string.phone_error_percent),
            enabled = !socFromCar,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (socFromCar) {
            Text(
                text = stringResource(R.string.phone_soc_source_car),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
        }

        CarHardwareStatus(
            diagnostics = diagnostics,
            modifier = Modifier.padding(top = 24.dp),
        )

        OutlinedButton(
            onClick = {
                name = ""; battery = ""; consumption = ""; connectors = emptySet()
                onVehicleChange(null)
            },
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.phone_action_delete_vehicle))
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            // Decimal, not integer: consumption values like 17.8 are the norm.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (isError) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Accept the German decimal comma — otherwise entering "17,8" would fail. */
private fun String.toPositiveDoubleOrNull(): Double? =
    replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0.0 }

private fun String.toPercentOrNull(): Double? =
    replace(',', '.').trim().toDoubleOrNull()?.takeIf { it in 0.0..100.0 }

/** Display whole numbers without ".0" — 77 instead of 77.0. */
private fun Double.asInput(): String {
    val rounded = kotlin.math.round(this)
    return if (kotlin.math.abs(this - rounded) < 0.001) rounded.toLong().toString() else toString()
}
