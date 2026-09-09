package de.autoapp.android.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.autoapp.android.R
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.PrefRow
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.android.phone.theme.tabular
import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.MapLabelStyle
import de.autoapp.shared.ui.DrawerUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DrawerContent(
    uiState: DrawerUiState,
    onOpen: (PhoneDestination) -> Unit,
    onFilters: (ChargeFilters) -> Unit,
    onLabelStyle: (MapLabelStyle) -> Unit,
) {
    val filters = uiState.filters

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The mockup's drawer-head: bolt + wordmark over a hairline.
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.padding(vertical = 14.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_bolt),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_preferences))
            PrefRow(
                icon = painterResource(R.drawable.ic_car),
                label = stringResource(R.string.drawer_car),
                sublabel = uiState.vehicleName ?: stringResource(R.string.drawer_car_none),
                onClick = { onOpen(Garage) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            PrefRow(
                icon = painterResource(R.drawable.ic_cardpay),
                label = stringResource(R.string.drawer_subscriptions),
                sublabel = stringResource(R.string.drawer_subs_count, uiState.activeTariffCount),
                onClick = { onOpen(Subscriptions) },
            )
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_filters))
            PrefRow(
                icon = painterResource(R.drawable.ic_filter),
                label = stringResource(R.string.drawer_networks),
                sublabel = networksSummary(uiState.preferredNetworkCount),
                onClick = { onOpen(Networks) },
            )
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_min_power))
            PowerSegments(filters, onFilters)
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_max_price, filters.maxPriceEuroPerKwh.twoDecimals()))
            Slider(
                value = filters.maxPriceEuroPerKwh.toFloat(),
                onValueChange = { onFilters(filters.copy(maxPriceEuroPerKwh = (it * 100).roundToInt() / 100.0)) },
                valueRange = 0.4f..1.0f,
            )
            SectionLabel(stringResource(R.string.drawer_max_distance, filters.maxDistanceKm.oneDecimal()))
            Slider(
                value = filters.maxDistanceKm.toFloat(),
                onValueChange = { onFilters(filters.copy(maxDistanceKm = (it * 2).roundToInt() / 2.0)) },
                valueRange = 1f..10f,
            )
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_map_label))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.labelStyle == MapLabelStyle.PRICE,
                    onClick = { onLabelStyle(MapLabelStyle.PRICE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.drawer_label_price)) }
                SegmentedButton(
                    selected = uiState.labelStyle == MapLabelStyle.FREE_CHARGERS,
                    onClick = {},
                    enabled = false,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.drawer_label_free)) }
            }
            Fineprint(stringResource(R.string.drawer_label_free_note), modifier = Modifier.padding(top = 6.dp))
        }

        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel(stringResource(R.string.drawer_debug), modifier = Modifier.padding(top = 12.dp))
            PrefRow(
                icon = painterResource(R.drawable.ic_send),
                label = stringResource(R.string.drawer_cardata),
                onClick = { onOpen(CarData) },
            )
        }

        Fineprint(stringResource(R.string.drawer_availability_note))
    }
}

/**
 * "Alle Netze" or the number of picked ones — the same line in the drawer and
 * in the network screen's top bar, so it lives in one place.
 */
@Composable
internal fun networksSummary(preferredCount: Int): String =
    if (preferredCount > 0) {
        stringResource(R.string.drawer_networks_selected, preferredCount)
    } else {
        stringResource(R.string.drawer_networks_all)
    }

/** The mockup's `.seg`: soft track, white active segment with blue text. */
@Composable
private fun PowerSegments(filters: ChargeFilters, onFilters: (ChargeFilters) -> Unit) {
    val steps = listOf(50.0, 150.0, 300.0)
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(3.dp),
    ) {
        steps.forEach { step ->
            val selected = filters.minPowerKw == step
            Surface(
                onClick = { onFilters(filters.copy(minPowerKw = step)) },
                shape = MaterialTheme.shapes.extraSmall,
                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                shadowElevation = if (selected) 1.dp else 0.dp,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "${step.roundToInt()} kW",
                    style = MaterialTheme.typography.labelMedium.tabular,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}
