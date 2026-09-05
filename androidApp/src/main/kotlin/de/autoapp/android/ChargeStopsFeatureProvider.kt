package de.autoapp.android

import android.content.Context
import androidx.car.app.CarContext
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.ChargeStopsFeatureFactory
import de.autoapp.shared.currentTimeMillis
import de.autoapp.shared.data.FusedLocationSource
import de.autoapp.shared.db.DatabaseDriverFactory
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.TimeProvider
import de.autoapp.shared.settings.PersistentSettingsStore
import de.autoapp.shared.settings.SharedPreferencesStorage
import de.autoapp.android.car.CarHardwareSoCSource

/**
 * Plugs the Android-specific parts into the shared assembly: location via
 * Play Services, the key from `local.properties` (see
 * androidApp/build.gradle.kts), and, if the head unit plays along, the
 * vehicle's state of charge. Everything else is decided by
 * [ChargeStopsFeatureFactory] — the same for both platforms.
 */
object ChargeStopsFeatureProvider {

    private val timeProvider = TimeProvider { currentTimeMillis() }

    @Volatile
    private var store: SettingsStore? = null

    /**
     * One instance for the whole app.
     *
     * Not negotiable: the phone UI writes here and the feature reads the
     * same flows. Two instances over the same storage would each stay blind
     * to the driver's changes made through the other until the app
     * restarts — and in Android Auto, the phone and car UI run in the same process.
     */
    fun settingsStore(context: Context): SettingsStore =
        store ?: synchronized(this) {
            store ?: PersistentSettingsStore(
                SharedPreferencesStorage(context.applicationContext),
            ).also { store = it }
        }

    /** For the phone UI: no vehicle access, just location and manual input. */
    fun create(context: Context): ChargeStopsFeature =
        ChargeStopsFeatureFactory.create(
            locationSource = FusedLocationSource(context),
            openChargeMapKey = BuildConfig.OPEN_CHARGE_MAP_API_KEY,
            settingsStore = settingsStore(context),
            databaseDriverFactory = DatabaseDriverFactory(context),
            timeProvider = timeProvider,
        )

    /** For Android Auto: additionally the vehicle's state of charge, if it provides one. */
    fun createForCar(carContext: CarContext): ChargeStopsFeature =
        ChargeStopsFeatureFactory.create(
            locationSource = FusedLocationSource(carContext),
            openChargeMapKey = BuildConfig.OPEN_CHARGE_MAP_API_KEY,
            settingsStore = settingsStore(carContext),
            databaseDriverFactory = DatabaseDriverFactory(carContext),
            hardwareSoCSource = CarHardwareSoCSource(
                carContext = carContext,
                time = timeProvider,
                settingsStore = settingsStore(carContext),
            ),
            timeProvider = timeProvider,
        )
}
