package org.julakali.chargeahead.android.phone

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.android.phone.components.AppSlider
import org.julakali.chargeahead.android.phone.components.PrefRow
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Switch
import org.julakali.chargeahead.android.phone.components.SectionLabel
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.ui.DrawerUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DrawerContent(
    uiState: DrawerUiState,
    onOpen: (PhoneDestination) -> Unit,
    onFilters: (ChargeFilters) -> Unit,
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
        // Drawer head: bolt + wordmark over a hairline.
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
            AcModeToggle(
                active = filters.slowMode,
                onToggle = { onFilters(filters.copy(slowMode = it)) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_max_distance, filters.maxDistanceKm.oneDecimal()))
            AppSlider(
                value = filters.maxDistanceKm.toFloat(),
                onValueChange = { onFilters(filters.copy(maxDistanceKm = (it * 2).roundToInt() / 2.0)) },
                valueRange = 1f..10f,
            )
        }

        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel(stringResource(R.string.drawer_about), modifier = Modifier.padding(top = 12.dp))
            PrefRow(
                icon = painterResource(R.drawable.ic_info),
                label = stringResource(R.string.drawer_legal),
                onClick = { onOpen(Legal) },
            )
            PrefRow(
                icon = painterResource(R.drawable.ic_document),
                label = stringResource(R.string.drawer_licenses),
                onClick = { onOpen(Licenses) },
            )
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
    }
}

/** "Alle Netze" or the number of picked ones. */
@Composable
internal fun networksSummary(preferredCount: Int): String =
    if (preferredCount > 0) {
        stringResource(R.string.drawer_networks_selected, preferredCount)
    } else {
        stringResource(R.string.drawer_networks_all)
    }

/** The one lit-up control in the drawer: a card that tints when AC mode is on. */
@Composable
private fun AcModeToggle(active: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    // Optimistic: flips on tap, reconciles with [active].
    var shown by remember { mutableStateOf(active) }
    LaunchedEffect(active) { shown = active }

    val container by animateColorAsState(
        if (shown) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        label = "acContainer",
    )
    val accent by animateColorAsState(
        if (shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "acAccent",
    )
    Surface(
        onClick = {
            shown = !shown
            onToggle(shown)
        },
        shape = MaterialTheme.shapes.medium,
        color = container,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // A small "AC" badge that lights up with the mode.
            Surface(shape = MaterialTheme.shapes.small, color = accent.copy(alpha = if (shown) 1f else 0.12f)) {
                Text(
                    "AC",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (shown) MaterialTheme.colorScheme.onPrimary else accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.drawer_slow_mode), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.drawer_slow_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = shown, onCheckedChange = null)
        }
    }
}

/** Segmented control: soft track, white active segment with blue text. */
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
