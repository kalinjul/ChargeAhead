package de.autoapp.android.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppSheet
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.NetworkDot
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.domain.ChargeStop

/**
 * The same information as in the car, so the two UIs don't drift apart — as
 * a bottom sheet over the map. A sheet and not a dialog: the map stays
 * visible above it, so the pin the driver just tapped keeps its context, and
 * the sheet dismisses with the same downward swipe as every other sheet here.
 */
@Composable
fun ChargeStopDetailSheet(stop: ChargeStop, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AppSheet(onDismissRequest = onDismiss) {
        Column {
            // The network as the headline: that is what the driver decides
            // by. The site name is not shown at all — it is either the town
            // again ("Kiel"), which the address line already carries, or an
            // operator's internal id ("DE*CNT*EP00214*001").
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                NetworkDot(operatorColor(stop.site.operator))
                Text(
                    stop.site.operator ?: stop.site.name,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            ChargeStopFormatter.addressLine(stop)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        Column {
            Text(ChargeStopFormatter.primaryLine(stop), style = MaterialTheme.typography.titleSmall)
            Text(
                ChargeStopFormatter.secondaryLine(stop),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column {
            SectionLabel(stringResource(R.string.phone_detail_connectors))
            val connectorLines = ChargeStopFormatter.connectorLines(stop)
            if (connectorLines.isEmpty()) {
                Text(
                    stringResource(R.string.phone_detail_unknown_connectors),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                connectorLines.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ChargeStopFormatter.sourceLine(stop)?.let { source ->
            Fineprint(stringResource(R.string.phone_detail_source, source))
        }

        Button(
            onClick = { context.startNavigationTo(stop); onDismiss() },
            shape = MaterialTheme.shapes.medium,
            // AppSheet leaves the bottom inset to whatever ends the sheet.
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_destination),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(stringResource(R.string.phone_detail_navigate))
        }
    }
}

/**
 * Coordinates **and** name: the coordinates lead exactly there, the name
 * appears to the driver as the destination.
 */
private fun Context.startNavigationTo(stop: ChargeStop) {
    val position = stop.site.position
    val label = Uri.encode(stop.site.name)
    val uri = Uri.parse("geo:${position.lat},${position.lon}?q=${position.lat},${position.lon}($label)")

    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (notFound: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.phone_detail_no_navigation), Toast.LENGTH_LONG).show()
    }
}
