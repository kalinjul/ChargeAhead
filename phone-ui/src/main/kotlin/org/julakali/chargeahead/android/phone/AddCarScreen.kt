package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.components.SearchField
import org.julakali.chargeahead.android.phone.components.TickRow
import org.julakali.chargeahead.android.phone.components.TickStyle
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.ui.AddCarUiState
import org.julakali.chargeahead.shared.ui.AddCarViewModel
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun AddCarRoute(
    onAdded: (VehiclePreset) -> Unit,
    onCustomCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddCarViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AddCarScreen(
        uiState = uiState,
        onSearchChange = viewModel::onQueryChanged,
        onAdd = { preset ->
            viewModel.onPresetAdded(preset)
            onAdded(preset)
        },
        onCreateCustom = { name ->
            viewModel.onCustomCarCreated(name)
            onCustomCreated()
        },
        modifier = modifier,
    )
}

@Composable
fun AddCarScreen(
    uiState: AddCarUiState,
    onSearchChange: (String) -> Unit,
    onAdd: (VehiclePreset) -> Unit,
    onCreateCustom: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hits = uiState.matches
    val query = uiState.query.trim()
    val defaultName = stringResource(R.string.addcar_custom_default_name)

    Column(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SearchField(
            value = uiState.query,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.garage_search),
            modifier = Modifier.padding(top = 12.dp),
        )
        AppCard(modifier = Modifier.weight(1f, fill = false).padding(bottom = 12.dp)) {
            LazyColumn {
                itemsIndexed(hits, key = { _, preset -> preset.id }) { index, preset ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    TickRow(
                        label = preset.name,
                        sublabel = stringResource(
                            R.string.garage_preset_line,
                            preset.usableBatteryKwh.oneDecimal(),
                            preset.consumptionKwhPer100Km.oneDecimal(),
                            preset.dcPeakPowerKw.roundToInt(),
                        ),
                        checked = false,
                        tick = TickStyle.ADD,
                        onClick = { onAdd(preset) },
                    )
                }
                item(key = CUSTOM_KEY) {
                    if (hits.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    TickRow(
                        label = if (query.isEmpty()) stringResource(R.string.addcar_custom) else stringResource(R.string.addcar_custom_named, query),
                        sublabel = stringResource(R.string.addcar_custom_hint),
                        checked = false,
                        tick = TickStyle.ADD,
                        onClick = { onCreateCustom(query.ifEmpty { defaultName }) },
                    )
                }
            }
        }
    }
}

/** Can't clash with a catalog id. */
private const val CUSTOM_KEY = "custom-car"
