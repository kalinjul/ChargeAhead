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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.ui.VehicleSettingsUiState
import de.autoapp.shared.ui.VehicleSettingsViewModel

/**
 * Free-form entry of battery capacity and consumption — deliberately no
 * vehicle list (ARCHITECTURE.md, open point 4).
 *
 * Written immediately on every valid change, not only on "Save". A state of
 * charge the driver types in that fails to land because of a forgotten tap
 * would be the worst way for the reachability calculation to go wrong.
 */
@Composable
fun VehicleSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: VehicleSettingsViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    VehicleSettingsScreen(
        uiState = uiState,
        onNameChange = viewModel::onNameChanged,
        onBatteryChange = viewModel::onBatteryChanged,
        onConsumptionChange = viewModel::onConsumptionChanged,
        onConnectorToggle = viewModel::onConnectorToggled,
        onSocChange = viewModel::onSocChanged,
        onClear = viewModel::onVehicleCleared,
        modifier = modifier,
    )
}

@Composable
fun VehicleSettingsScreen(
    uiState: VehicleSettingsUiState,
    onNameChange: (String) -> Unit,
    onBatteryChange: (String) -> Unit,
    onConsumptionChange: (String) -> Unit,
    onConnectorToggle: (ConnectorType, Boolean) -> Unit,
    onSocChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Fineprint(
            text = stringResource(R.string.phone_settings_intro),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.phone_field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        NumberField(
            value = uiState.battery,
            onValueChange = onBatteryChange,
            label = stringResource(R.string.phone_field_battery),
            isError = uiState.batteryInvalid,
            errorText = stringResource(R.string.phone_error_positive_number),
        )

        NumberField(
            value = uiState.consumption,
            onValueChange = onConsumptionChange,
            label = stringResource(R.string.phone_field_consumption),
            isError = uiState.consumptionInvalid,
            errorText = stringResource(R.string.phone_error_positive_number),
        )

        SectionLabel(
            text = stringResource(R.string.phone_connectors_heading),
            modifier = Modifier.padding(top = 16.dp),
        )
        Fineprint(
            text = stringResource(R.string.phone_connectors_hint),
        )
        // UNKNOWN doesn't appear: "my car accepts unknown connectors" makes
        // no sense, and the planner treats unknown connectors leniently anyway.
        ConnectorType.entries.filter { it != ConnectorType.UNKNOWN }.forEach { type ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = type in uiState.connectors,
                    onCheckedChange = { checked -> onConnectorToggle(type, checked) },
                )
                Text(ChargeStopFormatter.connectorLabel(type))
            }
        }

        NumberField(
            value = uiState.socInput,
            onValueChange = onSocChange,
            label = stringResource(R.string.phone_field_soc),
            isError = uiState.socInvalid,
            errorText = stringResource(R.string.phone_error_percent),
            enabled = !uiState.socFromCar,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (uiState.socFromCar) {
            Fineprint(
                text = stringResource(R.string.phone_soc_source_car),
            )
        }

        CarHardwareStatus(
            diagnostics = uiState.diagnostics,
            modifier = Modifier.padding(top = 24.dp),
        )

        OutlinedButton(
            onClick = onClear,
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
