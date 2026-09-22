package org.julakali.chargeahead.uitests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.julakali.chargeahead.android.phone.HintChip
import org.julakali.chargeahead.android.phone.HomePill
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.RoundIcon
import org.julakali.chargeahead.android.phone.RoundIconButton
import org.julakali.chargeahead.android.phone.components.StationCard

@PreviewTest
@Preview(showBackground = true)
@Composable
fun HintChipPlain() {
    PreviewScaffold {
        Box(Modifier.padding(12.dp)) { HintChip("Auf der Karte wird nach Ladesäulen gesucht") }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun HomePillsChargeNowAndFavorites() {
    PreviewScaffold {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(12.dp)) {
            HomePill(
                text = "Jetzt laden",
                icon = painterResource(R.drawable.ic_battery),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                iconTint = MaterialTheme.colorScheme.tertiary,
                onClick = {},
            )
            HomePill(
                text = "Favoriten",
                icon = painterResource(R.drawable.ic_heart),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                iconTint = MaterialTheme.colorScheme.error,
                onClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun RoundIconButtonWithBadge() {
    PreviewScaffold {
        Box(Modifier.padding(12.dp)) {
            RoundIconButton(onClick = {}, badge = true) {
                RoundIcon(painterResource(R.drawable.ic_filter), contentDescription = "Filter")
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun StationCardChargeNow() {
    PreviewScaffold {
        Box(Modifier.width(400.dp).padding(12.dp)) {
            StationCard(
                rank = 1,
                badgeColor = Color(0xFF00A3E0),
                title = "Ionity",
                metaLine = "2,4 km · 350 kW",
                address = "Rasthof Hildesheimer Börde West, 31185 Söhlde",
                onSend = {},
                sendContentDescription = "Zu Ionity navigieren",
            )
        }
    }
}
