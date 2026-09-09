package de.autoapp.android

import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.ChargeStopsFeatureFactory
import de.autoapp.shared.currentTimeMillis
import de.autoapp.shared.data.FusedLocationSource
import de.autoapp.shared.db.DatabaseDriverFactory
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.TimeProvider
import de.autoapp.shared.settings.PersistentSettingsStore
import de.autoapp.shared.settings.SharedPreferencesStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The application-scoped singletons. One SettingsStore and one
 * ChargeStopsFeature per process — two instances would mean two location
 * streams and two stores blind to each other's writes; in Android Auto the
 * phone and car UI share this process (ARCHITECTURE.md §8).
 */
val appModule = module {
    single<TimeProvider> { TimeProvider { currentTimeMillis() } }

    single<SettingsStore> { PersistentSettingsStore(SharedPreferencesStorage(androidContext())) }

    // The phone's feature: no vehicle access, just location and manual input.
    // Never closed — its lifetime is the process.
    single<ChargeStopsFeature> {
        ChargeStopsFeatureFactory.create(
            locationSource = FusedLocationSource(androidContext()),
            openChargeMapKey = BuildConfig.OPEN_CHARGE_MAP_API_KEY,
            settingsStore = get(),
            databaseDriverFactory = DatabaseDriverFactory(androidContext()),
            timeProvider = get(),
        )
    }

    single {
        requireNotNull(get<ChargeStopsFeature>().planning) {
            "ChargeStopsFeatureFactory did not assemble a PlanningFeature"
        }
    }
}
