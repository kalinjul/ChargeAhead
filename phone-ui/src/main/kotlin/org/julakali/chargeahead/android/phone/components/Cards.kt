package org.julakali.chargeahead.android.phone.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    // Spelled out: in dark, surfaceContainerHigh equals surfaceVariant, which derives grey text.
    val colors = CardDefaults.elevatedCardColors(
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    )
    if (onClick != null) {
        ElevatedCard(onClick = onClick, shape = shape, colors = colors, modifier = modifier, content = content)
    } else {
        ElevatedCard(shape = shape, colors = colors, modifier = modifier, content = content)
    }
}
