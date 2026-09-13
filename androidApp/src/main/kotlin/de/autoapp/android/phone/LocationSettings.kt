package de.autoapp.android.phone

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
import de.autoapp.shared.data.FusedLocationSource
import de.autoapp.shared.logWarning
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The device-settings half of the location handshake.
 *
 * Holding the permission is not enough: the fused provider also needs the
 * device configured to serve the request. With Google Location Accuracy
 * ("Improve Location Accuracy") switched off it falls back to bare GNSS, and
 * without a view of the sky that means no fix ever arrives — silently, with no
 * error and no status-bar indicator (issue #36).
 *
 * Play Services answers exactly that question and hands back a resolution: the
 * system dialog that turns the setting on. Maps shows it, which is why Maps
 * works on such a device and we did not.
 *
 * Returns a callback; invoke it to run the check. [onSettled] fires once the
 * settings can serve us — immediately if they already could, otherwise after
 * the driver has accepted the dialog. A decline changes nothing and is not
 * reported: the next tap on the location button simply asks again.
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
                // Nothing to do but carry on — the stream may still deliver.
                logWarning("Could not show the location settings dialog", unusable)
            }
        }
    }
}

/**
 * `null` when the current settings already serve [FusedLocationSource]'s
 * request, otherwise the resolution to launch. A problem Play Services
 * considers unfixable (no Play Services, location hard-disabled by policy)
 * also yields `null` — there is no dialog to show for it.
 *
 * The check is built from [FusedLocationSource.locationRequest], not from a
 * request invented here: asking about different parameters would either miss
 * a real problem or invent one that never affects us.
 */
private suspend fun locationSettingsResolution(context: Context): PendingIntent? {
    val request = LocationSettingsRequest.Builder()
        .addLocationRequest(FusedLocationSource.locationRequest())
        // Ask even when the driver declined before — they reach this only by
        // tapping the location button, which is a request for exactly this.
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
