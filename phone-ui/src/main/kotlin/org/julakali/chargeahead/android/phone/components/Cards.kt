package org.julakali.chargeahead.android.phone.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.julakali.chargeahead.android.phone.theme.tabular

/**
 * The one card: elevated, no border, the route window's look everywhere.
 * [selected] swaps the container for the primary tint instead of drawing a frame.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    // White in light; in dark the default low container sinks into the page.
    val colors = CardDefaults.elevatedCardColors(
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    if (onClick != null) {
        ElevatedCard(onClick = onClick, shape = shape, colors = colors, modifier = modifier, content = content)
    } else {
        ElevatedCard(shape = shape, colors = colors, modifier = modifier, content = content)
    }
}

/** A two-column spec grid with uppercase keys. */
@Composable
fun KeyValueGrid(entries: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        entries.chunked(2).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.height(IntrinsicSize.Min)) {
                row.forEachIndexed { cellIndex, (key, value) ->
                    if (cellIndex > 0) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 13.dp, vertical = 11.dp)) {
                        Text(
                            key.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.titleSmall.tabular,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
