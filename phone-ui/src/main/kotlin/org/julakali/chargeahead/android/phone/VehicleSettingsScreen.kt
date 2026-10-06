package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Battery0Bar
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.julakali.chargeahead.android.phone.components.SettingRow
import org.julakali.chargeahead.android.phone.components.SettingsCard
import org.julakali.chargeahead.shared.ui.VehicleSettingsUiState
import org.julakali.chargeahead.shared.ui.VehicleSettingsViewModel
import org.julakali.chargeahead.shared.ui.VehicleEditor
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.garage_kw
import org.julakali.chargeahead.shared.resources.garage_kwh
import org.julakali.chargeahead.shared.resources.garage_kwh_per_100
import org.julakali.chargeahead.shared.resources.garage_unit_kw
import org.julakali.chargeahead.shared.resources.garage_unit_kwh
import org.julakali.chargeahead.shared.resources.garage_unit_kwh_per_100
import org.julakali.chargeahead.shared.resources.phone_action_restore_catalog_values
import org.julakali.chargeahead.shared.resources.soc_dialog_apply
import org.julakali.chargeahead.shared.resources.soc_dialog_cancel
import org.julakali.chargeahead.shared.resources.vehicle_battery
import org.julakali.chargeahead.shared.resources.vehicle_catalog_value
import org.julakali.chargeahead.shared.resources.vehicle_consumption
import org.julakali.chargeahead.shared.resources.vehicle_dc
import org.julakali.chargeahead.shared.resources.vehicle_dc_unknown
import org.julakali.chargeahead.shared.resources.vehicle_model
import org.julakali.chargeahead.shared.resources.vehicle_model_own
import org.julakali.chargeahead.shared.resources.vehicle_name
import org.julakali.chargeahead.shared.resources.vehicle_number_invalid
import org.julakali.chargeahead.shared.resources.vehicle_remove
import org.julakali.chargeahead.shared.resources.vehicle_remove_confirm
import org.julakali.chargeahead.shared.resources.vehicle_remove_text
import org.julakali.chargeahead.shared.resources.vehicle_remove_title
import org.julakali.chargeahead.shared.resources.vehicle_restore_current
import org.julakali.chargeahead.shared.resources.vehicle_restore_customized
import org.julakali.chargeahead.shared.resources.vehicle_restore_unavailable

@Composable
fun VehicleSettingsRoute(
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VehicleSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Pop only once stored: popping clears the ViewModel mid-write.
    LaunchedEffect(uiState.removed) { if (uiState.removed) onRemoved() }
    val actions = remember(viewModel) {
        VehicleSettingsActions(
            onNameOpen = viewModel::onNameEditOpened,
            onConsumptionOpen = viewModel::onConsumptionEditOpened,
            onBatteryOpen = viewModel::onBatteryEditOpened,
            onDcPeakOpen = viewModel::onDcPeakEditOpened,
            onInputChange = viewModel::onEditorInputChanged,
            onConfirm = viewModel::onEditorConfirmed,
            onDismiss = viewModel::onEditorDismissed,
            onRestoreCatalogValues = viewModel::onCatalogValuesRestored,
            onRemove = viewModel::onVehicleRemoveRequested,
            onRemoveConfirm = viewModel::onVehicleRemoveConfirmed,
            onRemoveCancel = viewModel::onVehicleRemoveCancelled,
        )
    }
    VehicleSettingsScreen(uiState = uiState, actions = actions, modifier = modifier)
}

class VehicleSettingsActions(
    val onNameOpen: () -> Unit = {},
    val onConsumptionOpen: () -> Unit = {},
    val onBatteryOpen: () -> Unit = {},
    val onDcPeakOpen: () -> Unit = {},
    val onInputChange: (String) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onRestoreCatalogValues: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRemoveConfirm: () -> Unit = {},
    val onRemoveCancel: () -> Unit = {},
)

@Composable
fun VehicleSettingsScreen(
    uiState: VehicleSettingsUiState,
    actions: VehicleSettingsActions,
    modifier: Modifier = Modifier,
) {
    val vehicle = uiState.vehicle ?: return
    val catalog = uiState.catalog?.toProfile()

    when (val editor = uiState.editor) {
        null -> Unit
        is VehicleEditor.Consumption -> FieldDialog(
            stringResource(Res.string.vehicle_consumption), editor.input, stringResource(Res.string.garage_unit_kwh_per_100), editor.applicable, actions,
            catalogValue = catalog?.let { stringResource(Res.string.garage_kwh_per_100, it.consumptionKwhPer100Km.oneDecimal()) },
        )
        is VehicleEditor.Name -> FieldDialog(
            stringResource(Res.string.vehicle_name), editor.input, unit = null, editor.applicable, actions,
            catalogValue = catalog?.displayName,
        )
        is VehicleEditor.Battery -> FieldDialog(
            stringResource(Res.string.vehicle_battery), editor.input, stringResource(Res.string.garage_unit_kwh), editor.applicable, actions,
            catalogValue = catalog?.let { stringResource(Res.string.garage_kwh, it.usableBatteryKwh.oneDecimal()) },
        )
        is VehicleEditor.DcPeak -> FieldDialog(
            stringResource(Res.string.vehicle_dc), editor.input, stringResource(Res.string.garage_unit_kw), editor.applicable, actions,
            catalogValue = catalog?.dcPeakPowerKw?.let { stringResource(Res.string.garage_kw, it.roundToInt()) },
        )
    }

    if (uiState.confirmingRemoval) {
        AlertDialog(
            onDismissRequest = actions.onRemoveCancel,
            title = { Text(stringResource(Res.string.vehicle_remove_title, vehicle.displayName)) },
            text = { Text(stringResource(Res.string.vehicle_remove_text)) },
            confirmButton = {
                TextButton(
                    onClick = actions.onRemoveConfirm,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.vehicle_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = actions.onRemoveCancel) { Text(stringResource(Res.string.soc_dialog_cancel)) } },
        )
    }

    // Scrolls in landscape or with large fonts; otherwise the button sits at the bottom.
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(
                listOf {
                    SettingRow(
                        Icons.Outlined.DirectionsCar,
                        stringResource(Res.string.vehicle_model),
                        onClick = null,
                        value = uiState.catalog?.name ?: stringResource(Res.string.vehicle_model_own),
                    )
                },
            )
            SettingsCard(
                listOf(
                    {
                        SettingRow(Icons.Outlined.Badge, stringResource(Res.string.vehicle_name), actions.onNameOpen, value = vehicle.displayName)
                    },
                    {
                        SettingRow(
                            Icons.Outlined.Speed,
                            stringResource(Res.string.vehicle_consumption),
                            actions.onConsumptionOpen,
                            value = stringResource(Res.string.garage_kwh_per_100, vehicle.consumptionKwhPer100Km.oneDecimal()),
                        )
                    },
                    {
                        SettingRow(
                            Icons.Outlined.Battery0Bar,
                            stringResource(Res.string.vehicle_battery),
                            actions.onBatteryOpen,
                            value = stringResource(Res.string.garage_kwh, vehicle.usableBatteryKwh.oneDecimal()),
                        )
                    },
                    {
                        SettingRow(
                            Icons.Outlined.Bolt,
                            stringResource(Res.string.vehicle_dc),
                            actions.onDcPeakOpen,
                            value = vehicle.dcPeakPowerKw?.let { stringResource(Res.string.garage_kw, it.roundToInt()) }
                                ?: stringResource(Res.string.vehicle_dc_unknown),
                        )
                    },
                ),
            )

            // Always shown so it can be found; enabled only where it applies.
            SettingsCard(
                listOf {
                    SettingRow(
                        Icons.Outlined.Restore,
                        stringResource(Res.string.phone_action_restore_catalog_values),
                        actions.onRestoreCatalogValues,
                        supporting = stringResource(
                            when {
                                uiState.catalog == null -> Res.string.vehicle_restore_unavailable
                                uiState.canRestoreCatalogValues -> Res.string.vehicle_restore_customized
                                else -> Res.string.vehicle_restore_current
                            },
                        ),
                        enabled = uiState.canRestoreCatalogValues,
                        leadsOn = false,
                    )
                },
            )

            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = actions.onRemove,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Text(stringResource(Res.string.vehicle_remove))
            }
        }
    }
}

@Composable
private fun FieldDialog(
    title: String,
    input: String,
    unit: String?,
    applicable: Boolean,
    actions: VehicleSettingsActions,
    catalogValue: String? = null,
) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(title) },
        text = {
            FieldEditor(label = title, initial = input, unit = unit, applicable = applicable, catalogValue = catalogValue, onValueChange = actions.onInputChange)
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirm, enabled = applicable) { Text(stringResource(Res.string.soc_dialog_apply)) }
        },
        dismissButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(Res.string.soc_dialog_cancel)) } },
    )
}

/** Public for the previews. Keeps its own text: a ViewModel round trip could hand back an older value mid-word. */
@Composable
fun FieldEditor(
    label: String,
    initial: String,
    unit: String?,
    applicable: Boolean,
    onValueChange: (String) -> Unit,
    catalogValue: String? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val invalid = field.text.isNotBlank() && !applicable
    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            onValueChange(it.text)
        },
        label = { Text(label) },
        suffix = unit?.let { { Text(it) } },
        isError = invalid,
        supportingText = {
            Text(
                when {
                    invalid -> stringResource(Res.string.vehicle_number_invalid)
                    catalogValue != null -> stringResource(Res.string.vehicle_catalog_value, catalogValue)
                    else -> ""
                },
            )
        },
        singleLine = true,
        // A unit means a number.
        keyboardOptions = KeyboardOptions(keyboardType = if (unit != null) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

