package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 46dp floating circle; [badge] is the "filters customized" dot. */
@Composable
fun RoundIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(11.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

@Composable
fun RoundIcon(icon: Painter, contentDescription: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
}

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
) {
    Surface(onClick = onClick, shape = CircleShape, color = containerColor, contentColor = contentColor, shadowElevation = 6.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.padding(horizontal = if (text != null) 21.dp else 15.dp, vertical = 14.dp),
        ) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(18.dp))
            text?.let { Text(it, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)) }
        }
    }
}
