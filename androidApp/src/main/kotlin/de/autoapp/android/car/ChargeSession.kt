package de.autoapp.android.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.autoapp.android.BuildConfig
import de.autoapp.shared.ChargeStopsFeatureFactory
import de.autoapp.shared.data.FusedLocationSource
import de.autoapp.shared.db.DatabaseDriverFactory
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.TimeProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * One session per connection to the car host. The feature is created here and
 * shared by all screens: they plan on demand against the same location, charge
 * state, and settings, and it dies with the session.
 */
class ChargeSession : Session(), KoinComponent {

    override fun onCreateScreen(intent: Intent): Screen {
        val settings: SettingsStore = get()
        val time: TimeProvider = get()

        // The car's own feature: additionally the vehicle's charge state, if
        // the head unit provides one. The settings store is the app-scoped
        // singleton — the phone UI writes into the same flows.
        val feature = ChargeStopsFeatureFactory.create(
            locationSource = FusedLocationSource(carContext),
            openChargeMapKey = BuildConfig.OPEN_CHARGE_MAP_API_KEY,
            settingsStore = settings,
            databaseDriverFactory = DatabaseDriverFactory(carContext),
            hardwareSoCSource = CarHardwareSoCSource(
                carContext = carContext,
                time = time,
                settingsStore = settings,
            ),
            timeProvider = time,
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

        return CarHomeScreen(carContext, feature, settings)
    }
}
