package org.julakali.chargeahead.android.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.julakali.chargeahead.shared.ui.ShellEvent
import org.julakali.chargeahead.shared.ui.ShellViewModel

/**
 * The Android half of the location handshake: runs the permission request and
 * the device-settings check that [viewModel] asks for, and reports back.
 * [onSettled] fires once the device settings can serve a location request.
 */
@Composable
fun LocationHandshakeEffects(viewModel: ShellViewModel, onSettled: () -> Unit) {
    val context = LocalContext.current
    val event by viewModel.event.collectAsStateWithLifecycle()
    val checkLocationSettings = rememberLocationSettingsCheck(onSettled)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.onLocationPermissionResult(results.values.any { it })
    }

    // The grant can happen outside this launcher.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onLocationPermissionChecked(context.hasLocationPermission())
    }

    LaunchedEffect(event) {
        when (event) {
            null -> return@LaunchedEffect
            ShellEvent.RequestLocationPermission -> permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            ShellEvent.CheckLocationSettings -> checkLocationSettings()
        }
        viewModel.onEventHandled()
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
