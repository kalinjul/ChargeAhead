package de.autoapp.android.phone.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.autoapp.android.phone.theme.tabular

/** The mockup's `.card`: white, hairline border, 14dp corners, whisper of a shadow. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surface
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, border = border, shadowElevation = 1.dp, modifier = modifier) {
            Column(content = content)
        }
    } else {
        Surface(shape = shape, color = color, border = border, shadowElevation = 1.dp, modifier = modifier) {
            Column(content = content)
        }
    }
}

/** The mockup's `.kv`: a two-column spec grid with uppercase keys. */
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
