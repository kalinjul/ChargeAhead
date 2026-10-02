package org.julakali.chargeahead.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.settings.SettingsKeys.CHARGE_FILTERS
import org.julakali.chargeahead.shared.settings.SettingsKeys.ONLY_PREFERRED
import org.julakali.chargeahead.shared.settings.SettingsKeys.PREFERRED_NETWORKS

class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override val networks: Flow<NetworkPreferences> = dataStore.read { preferences ->
        val onlyPreferred = preferences.getStringOrNull(ONLY_PREFERRED)?.toBooleanStrictOrNull()
            ?: NetworkPreferences().onlyPreferred
        val preferred = preferences.getJson<List<String>>(PREFERRED_NETWORKS)
            .orEmpty().map { RENAMED_NETWORK_KEYS[it] ?: it }.toSet()
        NetworkPreferences(onlyPreferred, preferred)
    }

    // Slow mode lasts until the process ends; it is never written to the file.
    private val slowMode = MutableStateFlow(false)

    override val chargeFilters: Flow<ChargeFilters> = combine(
        dataStore.read { preferences ->
            preferences.getJson<StoredFilters>(CHARGE_FILTERS)
                ?.let { ChargeFilters(it.minPowerKw) }
                ?: ChargeFilters()
        },
        slowMode,
    ) { stored, slowMode -> stored.copy(slowMode = slowMode) }

    override suspend fun setNetworks(preferences: NetworkPreferences) {
        dataStore.edit {
            it.putString(ONLY_PREFERRED, preferences.onlyPreferred.toString())
            it.putJson(PREFERRED_NETWORKS, preferences.preferredOperators.takeIf { keys -> keys.isNotEmpty() }?.toList())
        }
    }

    override suspend fun setChargeFilters(filters: ChargeFilters) {
        dataStore.edit { it.putJson(CHARGE_FILTERS, StoredFilters(filters.minPowerKw)) }
        slowMode.value = filters.slowMode
    }

    @Serializable
    private data class StoredFilters(
        val minPowerKw: Double,
        // Older files still carry the slider's value; the parser rejects unknown keys.
        val maxDistanceKm: Double? = null,
    )

    private companion object {
        /** Network keys that were stored under an older name. */
        val RENAMED_NETWORK_KEYS = mapOf("ewe" to "ewe-go", "blink-charging-uk" to "blink-charging")
    }
}
