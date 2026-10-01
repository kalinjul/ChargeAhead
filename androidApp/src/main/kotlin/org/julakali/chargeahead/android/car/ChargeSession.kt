package org.julakali.chargeahead.android.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.shared.AppScope
import org.julakali.chargeahead.shared.data.FusedLocationSource
import org.julakali.chargeahead.shared.data.RememberingSoCSource
import org.julakali.chargeahead.shared.domain.CarDiagnosticsRepository
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.SessionFeature
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.ui.car.carSession
import org.koin.core.component.KoinComponent
import org.koin.core.parameter.parametersOf
import org.julakali.chargeahead.shared.domain.usecases.StartAppInteractor
import org.julakali.chargeahead.shared.domain.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.koin.core.component.get

/**
 * One session per connection to the car host. The feature is created here,
 * shared by all screens, and dies with the session.
 */
class ChargeSession : Session(), KoinComponent {

    override fun onCreateScreen(intent: Intent): Screen {
        val diagnostics: CarDiagnosticsRepository = get()
        val time: TimeProvider = get()
        val permissions = CarPermissions(carContext)
        val energyLevels = CarEnergyLevels(carContext, permissions, lifecycleScope)

        // The car's own feature, with the vehicle's charge state.
        val hardwareSoC = RememberingSoCSource(
            source = CarHardwareSoCSource(energyLevels = energyLevels, time = time, diagnosticsRepository = diagnostics),
            vehicles = get<VehicleRepository>(),
        )
        val feature: ChargeStopsFeature = get(SessionFeature) { parametersOf(FusedLocationSource(carContext), hardwareSoC) }
        val session = getKoin().carSession(feature)
        // A car session may be the first thing this process does.
        get<CoroutineScope>(AppScope).launch { get<StartAppInteractor>()() }

        // For the phone's debug view.
        val recorder = CarHardwareDebugRecorder(
            carContext = carContext,
            time = time,
            permissions = permissions,
            energyLevels = energyLevels,
            diagnostics = diagnostics,
            parentScope = get(AppScope),
            dispatchers = get(),
        )
        recorder.start()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Permissions may have changed while in the background.
                permissions.refresh()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                recorder.stop()
                session.close()
                feature.close()
            }
        })

        return CarHomeScreen(carContext, feature, session, permissions)
    }
}
