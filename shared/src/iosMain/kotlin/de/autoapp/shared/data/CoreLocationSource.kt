package de.autoapp.shared.data

import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.LocationSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

/**
 * Location source for iOS via CoreLocation.
 *
 * NOT COMPILED AND NOT VERIFIED. On the development machine (Linux), the iOS
 * target isn't even configured (see shared/build.gradle.kts); this code has
 * never seen a compiler. On the first Mac build, check the cinterop
 * signatures in particular: how `CLLocationCoordinate2D` is read out as a
 * `CValue`, and what the delegate methods are actually named in the
 * generated Kotlin binding.
 *
 * `flowOn(Dispatchers.Main)` is not incidental: `CLLocationManager` only
 * delivers its callbacks to a thread with a running run loop. Without it the
 * flow would stay silent forever, with no error raised anywhere.
 */
class CoreLocationSource : LocationSource {

    @OptIn(ExperimentalForeignApi::class)
    override val updates: Flow<Fix> = callbackFlow {
        val manager = CLLocationManager()

        // CLLocationManager only holds its delegate weakly. The local
        // reference must therefore stay alive until the end — it's touched
        // again below in awaitClose for exactly that reason.
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {

            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                trySend(location.toFix())
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                // Permission denial also arrives here. The flow terminates,
                // the feature reports LOCATION_UNAVAILABLE.
                close(IllegalStateException(didFailWithError.localizedDescription))
            }
        }

        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()

        awaitClose {
            manager.stopUpdatingLocation()
            manager.delegate = null
            delegate.description
        }
    }.flowOn(Dispatchers.Main)

    @OptIn(ExperimentalForeignApi::class)
    private fun CLLocation.toFix(): Fix {
        val position = coordinate.useContents { LatLon(latitude, longitude) }
        return Fix(
            position = position,
            // CoreLocation signals invalidity via negative values, not a
            // dedicated flag — 0 is a valid value (north, or standing still).
            bearingDeg = course.takeIf { it >= 0.0 },
            speedMps = speed.takeIf { it >= 0.0 },
            timestampMillis = (timestamp.timeIntervalSince1970 * 1000.0).toLong(),
        )
    }
}
