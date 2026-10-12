package org.julakali.chargeahead.shared.data

import kotlinx.coroutines.suspendCancellableCoroutine
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.ReverseGeocoder
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import kotlin.coroutines.resume

class CoreLocationReverseGeocoder : ReverseGeocoder {

    override suspend fun addressAt(position: LatLon): Address? {
        val geocoder = CLGeocoder()
        val placemark = suspendCancellableCoroutine { continuation ->
            geocoder.reverseGeocodeLocation(CLLocation(latitude = position.lat, longitude = position.lon)) { placemarks, _ ->
                continuation.resume(placemarks?.firstOrNull() as? CLPlacemark)
            }
            continuation.invokeOnCancellation { geocoder.cancelGeocode() }
        }
        return placemark?.toAddress()?.takeUnless { it.isEmpty }
    }
}

private fun CLPlacemark.toAddress(): Address {
    val street = thoroughfare?.let { road ->
        // The name has street and house number in the locale's order, unless it names a place instead.
        name?.takeIf { road in it } ?: listOfNotNull(road, subThoroughfare).joinToString(" ")
    }
    return Address(street = street, postalCode = postalCode, town = locality)
}
