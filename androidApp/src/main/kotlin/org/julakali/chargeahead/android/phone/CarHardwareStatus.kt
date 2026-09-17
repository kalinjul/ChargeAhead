package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.shared.currentTimeMillis
import org.julakali.chargeahead.shared.domain.SoCDiagnostics

/** Shows whether the state of charge came from the vehicle in the last car session. */
@Composable
fun CarHardwareStatus(
    diagnostics: SoCDiagnostics?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.phone_carhardware_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.phone_carhardware_intro),
            style = MaterialTheme.typography.bodySmall,
        )

        if (diagnostics == null) {
            Text(
                text = stringResource(R.string.phone_carhardware_never),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }

        val detail = diagnostics.detail.orEmpty()
        val message = when (diagnostics.outcome) {
            SoCDiagnostics.Outcome.AVAILABLE ->
                stringResource(R.string.phone_carhardware_available, detail)

            SoCDiagnostics.Outcome.NO_DATA ->
                stringResource(R.string.phone_carhardware_no_data, detail)

            SoCDiagnostics.Outcome.NO_PERMISSION ->
                stringResource(R.string.phone_carhardware_no_permission)

            SoCDiagnostics.Outcome.NO_CAR_HARDWARE ->
                stringResource(R.string.phone_carhardware_no_hardware)
        }

        Text(
            text = message,
            color = if (diagnostics.outcome == SoCDiagnostics.Outcome.AVAILABLE) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.phone_carhardware_checked, ago(diagnostics.checkedAtMillis)),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Coarse time indication, e.g. "3 hours ago". */
@Composable
private fun ago(millis: Long): String {
    val minutes = ((currentTimeMillis() - millis) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 60 -> stringResource(R.string.phone_duration_minutes, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.phone_duration_hours, (minutes / 60).toInt())
        else -> stringResource(R.string.phone_duration_days, (minutes / (60 * 24)).toInt())
    }
}
