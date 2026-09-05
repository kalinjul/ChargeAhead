package de.autoapp.android.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * One session per connection to the car host. For M0 there is only a single
 * screen, showing the static demo list.
 */
class ChargeSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return ChargeStopsScreen(carContext)
    }
}
