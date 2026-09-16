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

/**
 * Location source for Android via Google Play Services.
 *
 * Permission is not requested here — only the UI layer can do that, and in
 * Android Auto it goes through `CarContext.requestPermissions`. If it's
 * missing, the call throws a `SecurityException`; the flow then terminates
 * and the feature reports `LOCATION_UNAVAILABLE`. A silently empty flow would
 * be worse: it would look like "no location yet" and wait forever.
 *
 * Nor are the device's location *settings* checked here — that needs an
 * Activity to show the resolution dialog, so it lives in the phone UI
 * (`LocationSettings`) and is built from [locationRequest], the same request
 * this source subscribes with.
 */
class FusedLocationSource(
    context: Context,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val minDistanceMeters: Float = DEFAULT_MIN_DISTANCE_METERS,
) : LocationSource {

    // Application context, because the flow can outlive the lifetime of a
    // screen or an Activity.
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
            // fix — and with Google Location Accuracy off and no sky in view,
            // that can be never (issue #36). A stale fix beats an empty map;
            // the stream corrects it as soon as it has something better.
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
     *
     * `getCurrentLocation` is the one call that actively powers up the
     * hardware for a single answer — that is what makes the location button
     * do something on a device that has no fix yet.
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
        /**
         * 5 s at highway speed is roughly 180 m. Finer resolution wouldn't
         * help: recalculation only kicks in after 2 km or 60 s anyway.
         */
        const val DEFAULT_INTERVAL_MILLIS = 5_000L

        /** Below 50 m, movement can't be distinguished from location inaccuracy. */
        const val DEFAULT_MIN_DISTANCE_METERS = 50f

        /** Anything older than this is not worth waiting out a fresh fix for. */
        private const val MAX_CACHED_AGE_MILLIS = 60_000L

        /**
         * The request this source subscribes with. Public because the phone's
         * location-settings check has to ask about *this* request — asking
         * about a different one would either miss a problem or invent one.
         */
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
 *
 * Play Services ships its own `Task`; awaiting it without
 * kotlinx-coroutines-play-services is a listener pair, and that's cheaper
 * than pulling in the dependency for two call sites. A failed location task
 * is not exceptional — it means "no location", and every caller here treats
 * it that way.
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
