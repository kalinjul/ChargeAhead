package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.julakali.chargeahead.shared.domain.ChargeMode
import org.julakali.chargeahead.android.phone.theme.ChargeAheadColors
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.annotation.StringRes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import org.julakali.chargeahead.android.phone.theme.ChargeAheadMotion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.components.PrefRow
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Switch
import org.julakali.chargeahead.android.phone.components.SectionLabel
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.ui.DrawerUiState
import kotlin.math.roundToInt

/** What a drawer row asks for; the app decides which destination that is. */
enum class DrawerTarget { VEHICLE, NETWORKS, LEGAL, LICENSES, CAR_DATA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerContent(
    uiState: DrawerUiState,
    onOpen: (DrawerTarget) -> Unit,
    onFilters: (ChargeFilters) -> Unit,
    /** A mode button was tapped; [ChargeMode.NORMAL] when it was the one already on. */
    onModeSelected: (ChargeMode) -> Unit,
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
        Column {
            DrawerHead(Modifier.padding(vertical = 14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_preferences))
            PrefRow(
                icon = painterResource(R.drawable.ic_car),
                label = stringResource(R.string.drawer_car),
                sublabel = uiState.vehicleName ?: stringResource(R.string.drawer_car_none),
                onClick = { onOpen(DrawerTarget.VEHICLE) },
            )
            PrefRow(
                icon = painterResource(R.drawable.ic_filter),
                label = stringResource(R.string.drawer_networks),
                sublabel = networksSummary(uiState.preferredNetworkCount),
                onClick = { onOpen(DrawerTarget.NETWORKS) },
            )
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_min_power))
            PowerSegments(filters, onFilters)
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(stringResource(R.string.drawer_mode))
                ModeInfoButton()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ModeToggle(ChargeMode.AC, R.string.drawer_mode_ac, Icons.Outlined.Power, uiState.mode, onModeSelected, Modifier.weight(1f))
                ModeToggle(ChargeMode.BROWSE, R.string.mode_browse, Icons.Outlined.TravelExplore, uiState.mode, onModeSelected, Modifier.weight(1f))
            }
        }

        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel(stringResource(R.string.drawer_about), modifier = Modifier.padding(top = 12.dp))
            PrefRow(
                icon = painterResource(R.drawable.ic_info),
                label = stringResource(R.string.drawer_legal),
                onClick = { onOpen(DrawerTarget.LEGAL) },
            )
            PrefRow(
                icon = painterResource(R.drawable.ic_document),
                label = stringResource(R.string.drawer_licenses),
                onClick = { onOpen(DrawerTarget.LICENSES) },
            )
        }

        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel(stringResource(R.string.drawer_debug), modifier = Modifier.padding(top = 12.dp))
            PrefRow(
                icon = painterResource(R.drawable.ic_send),
                label = stringResource(R.string.drawer_cardata),
                onClick = { onOpen(DrawerTarget.CAR_DATA) },
            )
        }
    }
}

/** "Alle Netze" or the number of picked ones. */
@Composable
fun networksSummary(preferredCount: Int): String =
    if (preferredCount > 0) {
        stringResource(R.string.drawer_networks_selected, preferredCount)
    } else {
        stringResource(R.string.drawer_networks_all)
    }

/** One mode as a quick-settings tile with the shapes swapped: a squircle when off, a pill in the mode's colour when on. */
@Composable
private fun ModeToggle(
    mode: ChargeMode,
    @StringRes labelRes: Int,
    icon: ImageVector,
    current: ChargeMode,
    onSelect: (ChargeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = current == mode
    val corner by animateDpAsState(if (selected) MODE_KNOB_HEIGHT / 2 else 20.dp, ChargeAheadMotion.morph())
    val container by animateColorAsState(
        if (selected) ChargeAheadColors.forMode(mode) else MaterialTheme.colorScheme.surfaceContainerHighest,
        ChargeAheadMotion.effects(),
    )
    val ink by animateColorAsState(
        if (selected) ChargeAheadColors.onMode(mode) else MaterialTheme.colorScheme.onSurfaceVariant,
        ChargeAheadMotion.effects(),
    )

    Surface(
        selected = selected,
        // Tapping the one that is on releases it, tapping the other hands over: the two modes
        // exclude each other, and a dead button is a worse way to say so than a swap.
        onClick = { onSelect(if (selected) ChargeMode.NORMAL else mode) },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = ink,
        modifier = modifier.height(MODE_KNOB_HEIGHT),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(stringResource(labelRes), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

/** As tall as a quick-settings tile. */
private val MODE_KNOB_HEIGHT = 56.dp

/** What the two modes do, behind an i; a tap opens it, a tap outside closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeInfoButton() {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    // A tap on the i is also a tap outside the tooltip, which closes it before the click lands;
    // a touch older than the closing is that same tap and must not reopen it.
    var dismissedAt by remember { mutableLongStateOf(-1L) }
    var touchedAt by remember { mutableLongStateOf(0L) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        onDismissRequest = {
            dismissedAt = SystemClock.uptimeMillis()
            state.dismiss()
        },
        tooltip = {
            RichTooltip(title = { Text(stringResource(R.string.drawer_mode)) }) {
                Text(stringResource(R.string.drawer_mode_tooltip))
            }
        },
        state = state,
    ) {
        // Laid out at icon size, so the header row stays as tall as the label and its spacing
        // matches the sections around it; the touch area stays 48dp, the pointer layer expands it
        // past the bounds. SectionLabel pads 2dp above its text and 8dp below, so its optical centre
        // sits 3dp above its box centre: an offset, not padding, which would grow the row.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 24.dp) {
            IconButton(
                onClick = {
                    when {
                        state.isVisible -> state.dismiss()
                        touchedAt > dismissedAt -> scope.launch { state.show() }
                    }
                },
                modifier = Modifier
                    .size(24.dp)
                    .offset(y = (-3).dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            touchedAt = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).uptimeMillis
                        }
                    },
            ) {
                Icon(
                    painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.drawer_mode_info),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
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
                    stringResource(R.string.drawer_power_step, step.roundToInt()),
                    style = MaterialTheme.typography.labelMedium.tabular,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** The brand mark and wordmark; the wordmark drawable has a night variant. */
@Composable
fun DrawerHead(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        Image(painterResource(R.drawable.ic_powertrip), contentDescription = null, modifier = Modifier.height(24.dp))
        Image(
            painterResource(R.drawable.logo_powertrip_text),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.height(32.dp),
        )
    }
}
