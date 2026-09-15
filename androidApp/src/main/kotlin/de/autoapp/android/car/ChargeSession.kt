package de.autoapp.android.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.autoapp.shared.data.FusedLocationSource
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.TimeProvider
import de.autoapp.shared.newChargeStopsFeature
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * One session per connection to the car host. The feature is created here and
 * shared by all screens: they plan on demand against the same location, charge
 * state, and settings, and it dies with the session. Everything beneath it —
 * repository, database, HTTP client — is the app graph's, shared with the phone.
 */
class ChargeSession : Session(), KoinComponent {

    override fun onCreateScreen(intent: Intent): Screen {
        val settings: SettingsStore = get()
        val time: TimeProvider = get()

        // The car's own feature: additionally the vehicle's charge state, if
        // the head unit provides one.
        val feature = getKoin().newChargeStopsFeature(
            locationSource = FusedLocationSource(carContext),
            hardwareSoCSource = CarHardwareSoCSource(
                carContext = carContext,
                time = time,
                settingsStore = settings,
            ),
        )

        // Side channel for the phone's debug view: record whatever this head
        // unit delivers, for as long as the session lives.
        val recorder = CarHardwareDebugRecorder(
            carContext = carContext,
            time = time,
            settingsStore = settings,
        )
        recorder.start()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                recorder.stop()
                feature.close()
            }
        })

        return CarHomeScreen(carContext, feature, get(), settings)
    }
}
