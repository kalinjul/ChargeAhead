package org.julakali.chargeahead.android.phone.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.theme.DISABLED_ALPHA
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.soc_dialog_apply

@Composable
fun SettingsCard(rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
            row()
        }
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    leadsOn: Boolean = value == null && onClick != null,
) {
    // Disabled dims the title and icon only: the supporting line says why, so it stays readable.
    val dim = if (enabled) 1f else DISABLED_ALPHA
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim))
        },
        headlineContent = { Text(title, color = LocalContentColor.current.copy(alpha = dim)) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            when {
                // Purple says "tap to change"; a row that only shows keeps its value grey.
                value != null -> Text(
                    value,
                    style = MaterialTheme.typography.titleSmall.tabular,
                    color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                leadsOn -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) modifier.clickable(enabled = enabled, onClick = onClick) else modifier,
    )
}

@Composable
fun SettingSheetContent(
    title: String,
    applyEnabled: Boolean,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    applyLabel: String = stringResource(Res.string.soc_dialog_apply),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.padding(top = 20.dp), content = content)
        Row(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Spacer(Modifier.weight(1f))
            Button(onClick = onApply, enabled = applyEnabled) { Text(applyLabel) }
        }
    }
}

@Composable
fun BigValue(value: String, unit: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        Text(value, style = MaterialTheme.typography.displayMedium.tabular, fontWeight = FontWeight.Medium)
        Text(
            " $unit",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}
