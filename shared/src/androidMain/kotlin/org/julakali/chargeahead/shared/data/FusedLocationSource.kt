package org.julakali.chargeahead.shared.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FusedLocationSource(
    context: Context,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val minDistanceMeters: Float = DEFAULT_MIN_DISTANCE_METERS,
) : LocationSource {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    override val updates: Flow<Fix> = callbackFlow {
        val request = locationRequest(intervalMillis, minDistanceMeters)

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toFix()) }
            }
        }

        try {
            // Seed from what the platform already knows, before subscribing.
            // Without it the map shows nothing at all until the first streamed
            // fix
            client.lastLocation.awaitOrNull()?.let { trySend(it.toFix()) }

            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (missingPermission: SecurityException) {
            close(missingPermission)
            return@callbackFlow
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }

    /**
     * The cached fix if there is one, otherwise a freshly computed one.
     */
    @SuppressLint("MissingPermission")
    override suspend fun currentFix(): Fix? {
        val cached = try {
            client.lastLocation.awaitOrNull()
        } catch (missingPermission: SecurityException) {
            return null
        }
        if (cached != null) return cached.toFix()

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_CACHED_AGE_MILLIS)
            .build()
        return try {
            client.getCurrentLocation(request, CancellationTokenSource().token).awaitOrNull()?.toFix()
        } catch (missingPermission: SecurityException) {
            null
        }
    }

    private fun Location.toFix(): Fix = Fix(
        position = LatLon(latitude, longitude),
        // Check hasBearing()/hasSpeed() instead of testing for 0: 0° is a
        // valid bearing (north) and 0 m/s a valid speed (standing still).
        bearingDeg = if (hasBearing()) bearing.toDouble() else null,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        timestampMillis = time,
    )

    companion object {
        // TODO use Duration
        const val DEFAULT_INTERVAL_MILLIS = 5_000L

        /** Below 50 m, movement can't be distinguished from location inaccuracy. */
        const val DEFAULT_MIN_DISTANCE_METERS = 50f

        /** Anything older than this is not worth waiting out a fresh fix for. */
        private const val MAX_CACHED_AGE_MILLIS = 60_000L

        fun locationRequest(
            intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
            minDistanceMeters: Float = DEFAULT_MIN_DISTANCE_METERS,
        ): LocationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                .setMinUpdateDistanceMeters(minDistanceMeters)
                .build()
    }
}

/**
 * Awaits a Play Services task, `null` on failure.
 *A failed location task is not exceptional — it means "no location", and every caller here treats
 * it that way.
 * // TODO use kotlinx-coroutines-play-services instead
 */
internal suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error ->
        // A SecurityException is the missing permission and has to reach the
        // caller; everything else is just "no answer".
        if (error is SecurityException) continuation.resumeWithException(error) else continuation.resume(null)
    }
    addOnCanceledListener { continuation.resume(null) }
}
