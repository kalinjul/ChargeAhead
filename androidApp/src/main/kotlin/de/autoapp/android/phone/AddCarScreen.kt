package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.SearchField
import de.autoapp.android.phone.components.TickRow
import de.autoapp.android.phone.components.TickStyle
import de.autoapp.shared.domain.VehiclePreset
import de.autoapp.shared.ui.AddCarUiState
import de.autoapp.shared.ui.AddCarViewModel
import kotlin.math.roundToInt

@Composable
fun AddCarRoute(
    onAdded: (VehiclePreset) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddCarViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AddCarScreen(
        uiState = uiState,
        onSearchChange = viewModel::onQueryChanged,
        onAdd = { preset ->
            viewModel.onPresetAdded(preset)
            onAdded(preset)
        },
        modifier = modifier,
    )
}

/** The mockup's add-car screen: search the catalog, tap the +. */
@Composable
fun AddCarScreen(
    uiState: AddCarUiState,
    onSearchChange: (String) -> Unit,
    onAdd: (VehiclePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hits = uiState.matches

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
        if (hits.isEmpty()) {
            Fineprint(stringResource(R.string.garage_none_found))
        }
        LazyColumn {
            item {
                AppCard {
                    hits.forEachIndexed { index, preset ->
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
                }
            }
        }
    }
}
