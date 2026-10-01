package org.julakali.chargeahead.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.julakali.chargeahead.shared.domain.TripState
import org.julakali.chargeahead.shared.persistenceJson
import org.julakali.chargeahead.shared.domain.TripStorage

/** Trip state that older builds kept among the settings. */
interface LegacyTripSource {
    suspend fun legacyTrip(): TripState?

    suspend fun clearLegacyTrip()
}

/**
 * The trip state as one JSON entry in its own file, so a write replaces all
 * of it at once. Takes over [legacy]'s trip when nothing is stored yet.
 */
class DataStoreTripStorage(
    private val dataStore: DataStore<Preferences>,
    private val legacy: LegacyTripSource? = null,
) : TripStorage {

    override suspend fun read(): TripState? {
        val raw = dataStore.data.first()[KEY_STATE] ?: return takeLegacy()
        // A payload from an older or newer build costs the trip, not the launch.
        return runCatching { persistenceJson.decodeFromString<TripState>(raw) }.getOrNull()
    }

    override suspend fun write(state: TripState) {
        val raw = persistenceJson.encodeToString(state)
        dataStore.edit { it[KEY_STATE] = raw }
    }

    // Written here before it is cleared there, so a crash in between loses nothing.
    private suspend fun takeLegacy(): TripState? {
        val taken = legacy?.legacyTrip() ?: return null
        write(taken)
        legacy.clearLegacyTrip()
        return taken
    }

    private companion object {
        val KEY_STATE = stringPreferencesKey("trip.state")
    }
}
