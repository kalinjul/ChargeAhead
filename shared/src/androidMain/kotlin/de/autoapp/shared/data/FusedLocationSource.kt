package de.autoapp.shared.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.LocationSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Location source for Android via Google Play Services.
 *
 * Permission is not requested here — only the UI layer can do that, and in
 * Android Auto it goes through `CarContext.requestPermissions`. If it's
 * missing, the call throws a `SecurityException`; the flow then terminates
 * and the feature reports `LOCATION_UNAVAILABLE`. A silently empty flow would
 * be worse: it would look like "no location yet" and wait forever.
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
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toFix()) }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (missingPermission: SecurityException) {
            close(missingPermission)
            return@callbackFlow
        }

        awaitClose { client.removeLocationUpdates(callback) }
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
    }
}
