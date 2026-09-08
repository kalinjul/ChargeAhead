package de.autoapp.android.car

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import de.autoapp.android.R
import de.autoapp.shared.domain.LatLon

/**
 * Hands one charging site off to the host's navigation app.
 *
 * The app plans, Maps drives — it stays an addition beside the map, never a
 * replacement for it. `geo:` with coordinates **and** name in parentheses:
 * the coordinates lead exactly there, the name appears to the driver as the
 * destination. Name only would be a search with an uncertain outcome;
 * coordinates only would show up nameless in navigation.
 */
internal fun navigateTo(carContext: CarContext, name: String, position: LatLon) {
    val label = Uri.encode(name)
    val uri = Uri.parse("geo:${position.lat},${position.lon}?q=${position.lat},${position.lon}($label)")

    try {
        carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
    } catch (notFound: ActivityNotFoundException) {
        // Without a navigation app, the screen stays put instead of
        // silently doing nothing.
        CarToast.makeText(
            carContext,
            carContext.getString(R.string.car_no_navigation_app),
            CarToast.LENGTH_LONG,
        ).show()
    }
}
