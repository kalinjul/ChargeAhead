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
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.components.Fineprint
import org.julakali.chargeahead.android.phone.components.SearchField
import org.julakali.chargeahead.android.phone.components.TickRow
import org.julakali.chargeahead.android.phone.components.TickStyle
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.ui.AddCarUiState
import org.julakali.chargeahead.shared.ui.AddCarViewModel
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
            return@Column
        }
        AppCard(modifier = Modifier.weight(1f, fill = false).padding(bottom = 12.dp)) {
            LazyColumn {
                itemsIndexed(hits, key = { _, preset -> preset.name }) { index, preset ->
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
