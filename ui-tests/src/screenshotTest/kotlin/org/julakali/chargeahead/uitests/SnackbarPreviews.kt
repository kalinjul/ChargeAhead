package org.julakali.chargeahead.uitests

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.julakali.chargeahead.android.phone.components.AppSnackbar
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.active_route_ended

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 400)
@Composable
fun SnackbarTripEnded() = TripEnded()

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SnackbarTripEndedDark() = TripEnded()

@Composable
private fun TripEnded() {
    PreviewScaffold {
        Box(Modifier.padding(12.dp)) { AppSnackbar(StaticSnackbar(stringResource(Res.string.active_route_ended))) }
    }
}

/** A snackbar that is simply there, no host queue behind it. */
private class StaticSnackbar(message: String) : SnackbarData {
    override val visuals = object : SnackbarVisuals {
        override val message = message
        override val actionLabel: String? = null
        override val withDismissAction = false
        override val duration = SnackbarDuration.Short
    }

    override fun performAction() = Unit
    override fun dismiss() = Unit
}
