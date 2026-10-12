package org.julakali.chargeahead.shared.data

import android.content.Context
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.ReverseGeocoder
import kotlin.coroutines.resume

class AndroidReverseGeocoder(
    context: Context,
    private val io: CoroutineDispatcher,
) : ReverseGeocoder {

    private val geocoder = Geocoder(context.applicationContext)

    override suspend fun addressAt(position: LatLon): Address? {
        if (!Geocoder.isPresent()) return null
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(position.lat, position.lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                })
            }
        } else {
            runInterruptible(io) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(position.lat, position.lon, 1)?.firstOrNull()
            }
        }
        return found?.toAddress()?.takeUnless { it.isEmpty }
    }
}

private fun android.location.Address.toAddress() = Address(
    // The first address line has street and house number in the locale's order.
    street = thoroughfare?.let { getAddressLine(0)?.substringBefore(',')?.trim()?.takeIf(String::isNotEmpty) ?: it },
    postalCode = postalCode,
    town = locality ?: subAdminArea,
)
