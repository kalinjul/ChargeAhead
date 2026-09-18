package org.julakali.chargeahead.android.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.shared.data.FusedLocationSource
import org.julakali.chargeahead.shared.data.RememberingSoCSource
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.newChargeStopsFeature
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * One session per connection to the car host. The feature is created here,
 * shared by all screens, and dies with the session.
 */
class ChargeSession : Session(), KoinComponent {

    override fun onCreateScreen(intent: Intent): Screen {
        val settings: SettingsStore = get()
        val time: TimeProvider = get()
        val permissions = CarPermissions(carContext)
        val energyLevels = CarEnergyLevels(carContext, permissions, lifecycleScope)

        // The car's own feature, with the vehicle's charge state.
        val feature = getKoin().newChargeStopsFeature(
            locationSource = FusedLocationSource(carContext),
            hardwareSoCSource = RememberingSoCSource(
                source = CarHardwareSoCSource(
                    energyLevels = energyLevels,
                    time = time,
                    settingsStore = settings,
                ),
                settingsStore = settings,
            ),
        )

        // For the phone's debug view.
        val recorder = CarHardwareDebugRecorder(
            carContext = carContext,
            time = time,
            permissions = permissions,
            energyLevels = energyLevels,
            settingsStore = settings,
        )
        recorder.start()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Permissions may have changed while in the background.
                permissions.refresh()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                recorder.stop()
                feature.close()
            }
        })

        return CarHomeScreen(carContext, feature, settings, permissions)
    }
}
