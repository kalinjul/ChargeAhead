package org.julakali.chargeahead.android

import org.julakali.chargeahead.shared.AppScope
import org.julakali.chargeahead.shared.BackendConfig
import org.julakali.chargeahead.shared.data.DataStoreTripStorage
import org.julakali.chargeahead.shared.data.FusedLocationSource
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.TripStorage
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import org.julakali.chargeahead.shared.settings.createSettingsDataStore
import org.julakali.chargeahead.shared.settings.createTripDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * What only Android can supply to `chargeStopsModule`. One SettingsStore per
 * process, shared by the phone and car UI.
 */
val appModule = module {
    single { PersistentSettingsStore(createSettingsDataStore(androidContext(), dataStoreScope())) }
    single<SettingsStore> { get<PersistentSettingsStore>() }
    single<TripStorage> { DataStoreTripStorage(createTripDataStore(androidContext(), dataStoreScope()), legacy = get<PersistentSettingsStore>()) }

    // The phone's location; a car session passes its own.
    single<LocationSource> { FusedLocationSource(androidContext()) }
    single { DatabaseFactory(androidContext()) }

    single {
        requireNotNull(BackendConfig.of(BuildConfig.CHARGEAHEAD_BASE_URL, BuildConfig.CHARGEAHEAD_TOKEN)) {
            "chargeAheadBaseUrl and chargeAheadToken must be set in local.properties"
        }
    }
}

private fun Scope.dataStoreScope(): CoroutineScope = get<CoroutineScope>(AppScope) + get<AppCoroutineDispatchers>().io
