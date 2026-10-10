package org.julakali.chargeahead.android.phone

import org.julakali.chargeahead.android.phone.theme.DISABLED_ALPHA
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.map_compass
import org.julakali.chargeahead.shared.resources.map_zoom_hint

/** The bar's height: Material's search field is 56dp; the destination header matches it. */
val SEARCH_BAR_HEIGHT = 56.dp

val ROUND_BUTTON_SIZE = 46.dp

/** 46dp floating circle: the bar may be taller, the buttons stay light. */
@Composable
fun RoundIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.size(ROUND_BUTTON_SIZE),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun RoundIcon(icon: Painter, contentDescription: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
}

/** A status line floating over the map; not tappable, an action gets a chip. */
@Composable
fun HintChip(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Too far out for the chargers to load; tapping zooms in to where they show. */
@Composable
fun ZoomHintChip(onClick: () -> Unit) {
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text(stringResource(Res.string.map_zoom_hint)) },
        leadingIcon = { Icon(Icons.Outlined.ZoomIn, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

/** Fully round, floating pill. */
@Composable
fun HomePill(
    text: String?,
    icon: Painter,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    iconTint: Color = contentColor,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 6.dp,
    ) {
        // Only the content dims; a translucent pill would drag its shadow along.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .padding(horizontal = if (text != null) 21.dp else 15.dp, vertical = 14.dp),
        ) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(18.dp))
            text?.let { Text(it, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)) }
        }
    }
}

/** North needle, counter-rotated against the map's [bearing]. Shown only while the map is rotated. */
@Composable
fun CompassButton(bearing: () -> Float, onClick: () -> Unit) {
    RoundIconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Navigation,
            contentDescription = stringResource(Res.string.map_compass),
            tint = CompassRed,
            // graphicsLayer, so a turning map only invalidates the draw.
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = -bearing() },
        )
    }
}

private val CompassRed = Color(0xFFD93025)
