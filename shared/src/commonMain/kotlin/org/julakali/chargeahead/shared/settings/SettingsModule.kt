package org.julakali.chargeahead.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.julakali.chargeahead.shared.data.LegacyTripSource
import org.julakali.chargeahead.shared.domain.CarDiagnosticsRepository
import org.julakali.chargeahead.shared.domain.DestinationHistory
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * The settings repositories, all over the one file [createDataStore] opens.
 * One of each per process: the phone and car UI share them.
 */
fun settingsModule(createDataStore: Scope.() -> DataStore<Preferences>): Module = module {
    single(SETTINGS_FILE) { createDataStore() }
    single<VehicleRepository> { DataStoreVehicleRepository(get(SETTINGS_FILE)) }
    single<PreferencesRepository> { DataStorePreferencesRepository(get(SETTINGS_FILE)) }
    single<DestinationHistory> { DataStoreDestinationHistory(get(SETTINGS_FILE)) }
    single<CarDiagnosticsRepository> { DataStoreCarDiagnosticsRepository(get(SETTINGS_FILE)) }
    single<LegacyTripSource> { SettingsLegacyTripSource(get(SETTINGS_FILE)) }
}

private val SETTINGS_FILE = named("settings")
