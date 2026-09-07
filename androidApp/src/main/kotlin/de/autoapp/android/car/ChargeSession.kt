package de.autoapp.android.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.autoapp.android.ChargeStopsFeatureProvider
import de.autoapp.shared.currentTimeMillis
import de.autoapp.shared.domain.TimeProvider

/**
 * One session per connection to the car host. For M0 there is only a single
 * screen, showing the static demo list.
 */
class ChargeSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        // Side channel for the phone's debug view: record whatever this head
        // unit delivers, for as long as the session lives.
        val recorder = CarHardwareDebugRecorder(
            carContext = carContext,
            time = TimeProvider { currentTimeMillis() },
            settingsStore = ChargeStopsFeatureProvider.settingsStore(carContext),
        )
        recorder.start()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = recorder.stop()
        })

        return ChargeStopsScreen(carContext)
    }
}
