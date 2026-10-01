package org.julakali.chargeahead.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.julakali.chargeahead.shared.persistenceJson

/** The keys of the settings file; every repository over it writes only its own. */
internal object SettingsKeys {
    const val DESTINATIONS = "route.destinations"
    const val SOC_DIAGNOSTICS = "energy.socDiagnostics"
    const val ONLY_PREFERRED = "networks.onlyPreferred"
    const val PREFERRED_NETWORKS = "networks.preferred"
    const val NAME = "vehicle.displayName"
    const val BATTERY_KWH = "vehicle.usableBatteryKwh"
    const val CONSUMPTION = "vehicle.consumptionKwhPer100Km"
    const val CONNECTORS = "vehicle.acceptedConnectors"
    const val DC_PEAK = "vehicle.dcPeakPowerKw"
    const val GARAGE = "vehicle.garage"
    const val MANUAL_SOC = "energy.manualSocPercent"
    const val ARRIVAL_SOC = "energy.arrivalSocPercent"
    const val CHARGE_FILTERS = "filters.charge"
    const val COMMITTED_TRIP = "trip.committed"
    const val CAR_DEBUG = "car.debugData"

    /** Every key ever written to the settings; what a migration copies. */
    val ALL: Set<String> = setOf(
        DESTINATIONS, SOC_DIAGNOSTICS, ONLY_PREFERRED, PREFERRED_NETWORKS,
        NAME, BATTERY_KWH, CONSUMPTION, CONNECTORS, DC_PEAK, GARAGE,
        MANUAL_SOC, ARRIVAL_SOC, CHARGE_FILTERS, COMMITTED_TRIP, CAR_DEBUG,
    )
}

internal val settingsJson = persistenceJson

// Everything is stored as a string, as it was in SharedPreferences and
// NSUserDefaults, so the migrated values keep their keys and format.
internal fun Preferences.getStringOrNull(key: String): String? = this[stringPreferencesKey(key)]

/** `null` deletes the entry. */
internal fun MutablePreferences.putString(key: String, value: String?) {
    val preferencesKey = stringPreferencesKey(key)
    if (value == null) remove(preferencesKey) else this[preferencesKey] = value
}

internal inline fun <reified T> MutablePreferences.putJson(key: String, value: T?) {
    putString(key, value?.let { settingsJson.encodeToString(it) })
}

/** null on a missing key or a corrupt payload — the caller supplies the default. */
internal inline fun <reified T> Preferences.getJson(key: String): T? =
    getStringOrNull(key)?.let { raw -> runCatching { settingsJson.decodeFromString<T>(raw) }.getOrNull() }

/** One value of the file, re-emitted only when it changes. */
internal fun <T> DataStore<Preferences>.read(transform: (Preferences) -> T): Flow<T> =
    data.map(transform).distinctUntilChanged()
