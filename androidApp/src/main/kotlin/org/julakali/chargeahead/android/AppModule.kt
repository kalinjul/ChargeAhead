package org.julakali.chargeahead.android

import org.julakali.chargeahead.shared.BackendConfig
import org.julakali.chargeahead.shared.data.FusedLocationSource
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import org.julakali.chargeahead.shared.settings.createSettingsDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * What only Android can supply to `chargeStopsModule`. One SettingsStore per
 * process, shared by the phone and car UI.
 */
val appModule = module {
    single<SettingsStore> { PersistentSettingsStore(createSettingsDataStore(androidContext())) }

    // The phone's location; a car session passes its own.
    single<LocationSource> { FusedLocationSource(androidContext()) }
    single { DatabaseFactory(androidContext()) }

    single {
        requireNotNull(BackendConfig.of(BuildConfig.CHARGEAHEAD_BASE_URL, BuildConfig.CHARGEAHEAD_TOKEN)) {
            "chargeAheadBaseUrl and chargeAheadToken must be set in local.properties"
        }
    }
}
