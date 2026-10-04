package org.julakali.chargeahead.android.phone.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Material's snackbar host, its snackbars cut as pills like the buttons around them. */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { AppSnackbar(it) }
}

@Composable
fun AppSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    Snackbar(data, modifier, shape = CircleShape)
}
