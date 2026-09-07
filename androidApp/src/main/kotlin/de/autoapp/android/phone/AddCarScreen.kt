package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import de.autoapp.android.phone.components.SearchField
import de.autoapp.android.phone.components.TickRow
import de.autoapp.android.phone.components.TickStyle
import de.autoapp.shared.domain.VehicleCatalog
import de.autoapp.shared.domain.VehiclePreset
import kotlin.math.roundToInt

/** The mockup's add-car screen: search the catalog, tap the +. */
@Composable
fun AddCarScreen(owned: Set<String>, onAdd: (VehiclePreset) -> Unit, modifier: Modifier = Modifier) {
    var search by remember { mutableStateOf("") }
    val hits = VehicleCatalog.all.filter {
        it.name !in owned && it.name.contains(search.trim(), ignoreCase = true)
    }

    Column(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SearchField(
            value = search,
            onValueChange = { search = it },
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
