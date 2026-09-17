package org.julakali.chargeahead.android.phone

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import org.julakali.chargeahead.shared.data.FusedLocationSource
import org.julakali.chargeahead.shared.logWarning
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The device-settings half of the location handshake: shows the system
 * dialog that turns on Google Location Accuracy.
 *
 * Returns a callback; invoke it to run the check. [onSettled] fires once the
 * settings can serve the request. A decline is not reported.
 */
@Composable
fun rememberLocationSettingsCheck(onSettled: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnSettled by rememberUpdatedState(onSettled)

    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) latestOnSettled()
    }

    return {
        scope.launch {
            val resolution = locationSettingsResolution(context)
            if (resolution == null) {
                latestOnSettled()
                return@launch
            }
            try {
                resolutionLauncher.launch(IntentSenderRequest.Builder(resolution).build())
            } catch (unusable: Exception) {
                // The PendingIntent can be stale by the time it is launched.
                logWarning("Could not show the location settings dialog", unusable)
            }
        }
    }
}

/**
 * The resolution to launch, or `null` when the current settings already serve
 * [FusedLocationSource.locationRequest] or the problem is unfixable.
 */
private suspend fun locationSettingsResolution(context: Context): PendingIntent? {
    val request = LocationSettingsRequest.Builder()
        .addLocationRequest(FusedLocationSource.locationRequest())
        // Ask even when the driver declined before.
        .setAlwaysShow(true)
        .build()

    return suspendCancellableCoroutine { continuation ->
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(request)
            .addOnSuccessListener { continuation.resume(null) }
            .addOnFailureListener { error ->
                continuation.resume((error as? ResolvableApiException)?.resolution)
            }
            .addOnCanceledListener { continuation.resume(null) }
    }
}
