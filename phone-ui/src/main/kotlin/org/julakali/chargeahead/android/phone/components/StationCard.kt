package org.julakali.chargeahead.android.phone.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.theme.tabular

/** The charging-station card, shared by "charge now" and the trip review. */
@Composable
fun StationCard(
    rank: Int,
    badgeColor: Color,
    title: String,
    metaLine: String,
    address: String?,
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
            RankBadge(rank, badgeColor)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                address?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                MetaText(metaLine)
                extraLine?.let { MetaText(it) }
            }
            onSend?.let {
                GoButton(
                    // Every hand-off to Maps carries this icon.
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
