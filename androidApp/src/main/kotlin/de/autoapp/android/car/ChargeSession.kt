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
 * One session per connection to the car host. The feature is created here and
 * shared by all screens: they plan on demand against the same location, charge
 * state, and settings, and it dies with the session.
 */
class ChargeSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        val feature = ChargeStopsFeatureProvider.createForCar(carContext)

        // Side channel for the phone's debug view: record whatever this head
        // unit delivers, for as long as the session lives.
        val recorder = CarHardwareDebugRecorder(
            carContext = carContext,
            time = TimeProvider { currentTimeMillis() },
            settingsStore = ChargeStopsFeatureProvider.settingsStore(carContext),
        )
        recorder.start()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                recorder.stop()
                feature.close()
            }
        })

        return CarHomeScreen(carContext, feature)
    }
}
