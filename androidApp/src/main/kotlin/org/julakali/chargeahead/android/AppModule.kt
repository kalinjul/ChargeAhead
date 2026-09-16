package org.julakali.chargeahead.android

import org.julakali.chargeahead.shared.BackendConfig
import org.julakali.chargeahead.shared.ChargeStopsConfig
import org.julakali.chargeahead.shared.data.FusedLocationSource
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import org.julakali.chargeahead.shared.settings.SharedPreferencesStorage
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * What only Android can supply to `chargeStopsModule`: context-bound
 * location and database, the settings storage, and the build's config.
 * One SettingsStore per process — in Android Auto the phone and car UI share
 * this process, and two stores would be blind to each other's writes
 * (ARCHITECTURE.md §8).
 */
val appModule = module {
    // IO, not the store's Default: SharedPreferences commit() is a blocking disk write.
    single<SettingsStore> { PersistentSettingsStore(SharedPreferencesStorage(androidContext()), Dispatchers.IO) }

    // The phone's location; a car session passes its own.
    single<LocationSource> { FusedLocationSource(androidContext()) }
    single { DatabaseFactory(androidContext()) }

    single {
        ChargeStopsConfig(
            openChargeMapKey = BuildConfig.OPEN_CHARGE_MAP_API_KEY,
            backend = BackendConfig.of(BuildConfig.CHARGEAHEAD_BASE_URL, BuildConfig.CHARGEAHEAD_TOKEN),
        )
    }
}
