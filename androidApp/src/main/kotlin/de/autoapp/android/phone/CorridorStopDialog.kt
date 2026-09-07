package de.autoapp.android.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.domain.ChargeStop

/**
 * The same information as in the car, so the two UIs don't drift apart —
 * as a dialog over the map instead of its own screen.
 */
@Composable
fun ChargeStopDetailDialog(stop: ChargeStop, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stop.site.name) },
        text = {
            Column {
                Text(ChargeStopFormatter.primaryLine(stop))
                Text(
                    text = ChargeStopFormatter.secondaryLine(stop),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                stop.site.operator?.let {
                    Text(text = it, modifier = Modifier.padding(top = 8.dp))
                }
                ChargeStopFormatter.addressLine(stop)?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                SectionLabel(
                    text = stringResource(R.string.phone_detail_connectors),
                    modifier = Modifier.padding(top = 12.dp),
                )
                val connectorLines = ChargeStopFormatter.connectorLines(stop)
                if (connectorLines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.phone_detail_unknown_connectors),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    connectorLines.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                ChargeStopFormatter.sourceLine(stop)?.let { source ->
                    Text(
                        text = stringResource(R.string.phone_detail_source) + ": " + source,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { context.startNavigationTo(stop); onDismiss() }) {
                Text(stringResource(R.string.phone_detail_navigate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.phone_action_close))
            }
        },
    )
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
