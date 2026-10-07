package org.julakali.chargeahead.android.phone.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.shared.ui.ARRIVAL_SOC_RANGE
import org.julakali.chargeahead.shared.domain.SOC_RANGE
import kotlin.math.roundToInt
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.garage_percent
import org.julakali.chargeahead.shared.resources.soc_dialog_apply

enum class ChargeLevelKind(val range: IntRange, val picks: List<Int>) {
    NOW(SOC_RANGE, listOf(20, 40, 60, 80)),

    ARRIVAL(ARRIVAL_SOC_RANGE, listOf(10, 20, 30, 50)),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeLevelSheet(
    title: String,
    kind: ChargeLevelKind,
    percent: Int?,
    onChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    confirmLabel: String = stringResource(Res.string.soc_dialog_apply),
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ChargeLevelSheetContent(title, kind, percent, onChange, onConfirm, subtitle, confirmLabel)
    }
}

/** Public for the previews, which can't open a sheet window. */
@Composable
fun ChargeLevelSheetContent(
    title: String,
    kind: ChargeLevelKind,
    percent: Int?,
    onChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    subtitle: String? = null,
    confirmLabel: String = stringResource(Res.string.soc_dialog_apply),
) {
    val level = percent?.takeIf { it in kind.range }
    SettingSheetContent(
        title = title,
        subtitle = subtitle,
        applyEnabled = level != null,
        onApply = onConfirm,
        applyLabel = confirmLabel,
    ) {
        BigValue(level?.toString() ?: "–", "%")
        AppSlider(
            value = (level ?: kind.picks.last()).toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = kind.range.first.toFloat()..kind.range.last.toFloat(),
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            kind.picks.forEach { pick ->
                val on = pick == level
                FilterChip(
                    selected = on,
                    onClick = { onChange(pick) },
                    label = { Text(stringResource(Res.string.garage_percent, pick)) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
