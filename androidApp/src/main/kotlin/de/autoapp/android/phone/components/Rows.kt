package de.autoapp.android.phone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.theme.ChargeAheadColors
import de.autoapp.android.phone.theme.tabular

enum class TickStyle { CHECK, ADD, DELETE }

/** The mockup's `.netrow`: label, optional dot and sublabel, trailing tick circle. */
@Composable
fun TickRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    dotColor: Color? = null,
    tick: TickStyle = TickStyle.CHECK,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        dotColor?.let { NetworkDot(it) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            sublabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val scheme = MaterialTheme.colorScheme
        val (container, borderColor, iconTint) = when {
            tick == TickStyle.DELETE -> Triple(scheme.errorContainer, Color(0xFFF2B8B2), scheme.error)
            tick == TickStyle.ADD -> Triple(scheme.primaryContainer, Color(0xFFA8C7FA), scheme.primary)
            checked -> Triple(scheme.primary, scheme.primary, scheme.onPrimary)
            else -> Triple(Color.Transparent, scheme.outlineVariant, Color.Transparent)
        }
        Box(
            Modifier.size(22.dp).background(container, CircleShape).border(1.5.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when (tick) {
                TickStyle.CHECK -> R.drawable.ic_check
                TickStyle.ADD -> R.drawable.ic_add
                TickStyle.DELETE -> R.drawable.ic_remove
            }
            if (tick != TickStyle.CHECK || checked) {
                Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** The mockup's `.prefrow`: drawer entries with icon, summary and chevron. */
@Composable
fun PrefRow(icon: Painter, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, sublabel: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            sublabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("›", style = MaterialTheme.typography.bodyLarge, color = ChargeAheadColors.faint)
    }
}

/** The mockup's `.gobtn`/`.sendone`: the small square action at a row's end. */
@Composable
fun GoButton(
    icon: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.small, color = containerColor, modifier = modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}
