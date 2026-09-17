package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
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
 * `flowOn(Dispatchers.Main)`: `CLLocationManager` only delivers its callbacks
 * to a thread with a running run loop.
 */
class CoreLocationSource : LocationSource {

    @OptIn(ExperimentalForeignApi::class)
    override val updates: Flow<Fix> = callbackFlow {
        val manager = CLLocationManager()

        // CLLocationManager holds its delegate weakly; awaitClose keeps it alive.
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {

            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                trySend(location.toFix())
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                // Permission denial also arrives here.
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
            // CoreLocation signals invalidity via negative values.
            bearingDeg = course.takeIf { it >= 0.0 },
            speedMps = speed.takeIf { it >= 0.0 },
            timestampMillis = (timestamp.timeIntervalSince1970 * 1000.0).toLong(),
        )
    }
}
