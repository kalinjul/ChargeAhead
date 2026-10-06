package org.julakali.chargeahead.android.phone.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** A snackbar whose action shows [actionIcon] beside its label. */
class IconActionVisuals(
    override val message: String,
    override val actionLabel: String,
    val actionIcon: ImageVector,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
}

/** "No car yet", with the car that leads to the garage as its action. */
fun garageActionVisuals(message: String, actionLabel: String) =
    IconActionVisuals(message, actionLabel, Icons.Outlined.DirectionsCar)

/** Material's snackbar host with the app's snackbars. */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { AppSnackbar(it) }
}

/** Material's snackbar in the search bar's light colours instead of its inverse ones. */
@Composable
fun AppSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val visuals = data.visuals
    if (visuals !is IconActionVisuals) {
        Snackbar(
            data,
            modifier,
            containerColor = colors.surface,
            contentColor = colors.onSurface,
            actionColor = colors.primary,
            actionContentColor = colors.primary,
            dismissActionContentColor = colors.onSurfaceVariant,
        )
        return
    }
    Snackbar(
        // The data overload pads itself by this much; the slot overload leaves it to the caller.
        modifier = modifier.padding(12.dp),
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        action = {
            TextButton(onClick = data::performAction, colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)) {
                Icon(visuals.actionIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(visuals.actionLabel)
            }
        },
    ) {
        Text(visuals.message)
    }
}
