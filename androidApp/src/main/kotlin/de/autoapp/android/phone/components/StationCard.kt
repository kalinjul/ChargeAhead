package de.autoapp.android.phone.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.theme.tabular

/**
 * The one charging-station card — "charge now" and the trip review share it.
 * Headline is always the network; the trip review adds [extraLine] and makes
 * the card itself tappable for the section picker.
 */
@Composable
fun StationCard(
    rank: Int,
    title: String,
    metaLine: String,
    address: String?,
    priceEuroPerKwh: Double?,
    onSend: (() -> Unit)?,
    sendContentDescription: String?,
    modifier: Modifier = Modifier,
    extraLine: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    AppCard(
        onClick = onClick,
        modifier = if (selected) {
            modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
        } else {
            modifier
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
        ) {
            RankBadge(rank, MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MetaText(metaLine)
                address?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Two lines, not one: town and postal code are the
                        // point of this line, and they sit at the end.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                extraLine?.let { MetaText(it) }
            }
            priceEuroPerKwh?.let { PriceText(it) }
            onSend?.let {
                GoButton(
                    // The destination flag, not an arrow: every hand-off to
                    // Maps in this app carries the same icon.
                    icon = painterResource(R.drawable.ic_destination),
                    contentDescription = sendContentDescription,
                    onClick = it,
                )
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.tabular,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}
