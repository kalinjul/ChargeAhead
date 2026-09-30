package org.julakali.chargeahead.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.julakali.chargeahead.shared.data.LegacyTripSource
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.DestinationHistory
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.TripState
import org.julakali.chargeahead.shared.settings.SettingsKeys.COMMITTED_TRIP
import org.julakali.chargeahead.shared.settings.SettingsKeys.DESTINATIONS

class DataStoreDestinationHistory(
    private val dataStore: DataStore<Preferences>,
) : DestinationHistory {

    override val recentDestinations: Flow<List<Destination>> = dataStore.read { it.destinations() }

    override suspend fun addRecentDestination(destination: Destination) {
        dataStore.edit { preferences ->
            // Newest first, duplicates removed, length capped.
            val updated = (listOf(destination) + preferences.destinations().filterNot { it.position == destination.position })
                .take(MAX_RECENT_DESTINATIONS)
            preferences.putJson(
                DESTINATIONS,
                updated.map { StoredDestination(it.name, it.position.lat, it.position.lon, address = it.address) },
            )
        }
    }

    private fun Preferences.destinations(): List<Destination> =
        getJson<List<StoredDestination>>(DESTINATIONS).orEmpty().map(StoredDestination::toDomain)

    private companion object {
        const val MAX_RECENT_DESTINATIONS = 8
    }
}

/** The trip that older builds kept under the destination and committed-trip keys. */
// TODO drop once no install has a trip left under the settings keys
class SettingsLegacyTripSource(
    private val dataStore: DataStore<Preferences>,
) : LegacyTripSource {

    override suspend fun legacyTrip(): TripState? {
        val preferences = dataStore.data.first()
        val committed = preferences.getJson<CommittedTrip>(COMMITTED_TRIP)
        val destination = preferences.getJson<List<StoredDestination>>(DESTINATIONS)
            ?.firstOrNull { it.current }
            ?.toDomain()
            ?: committed?.plan?.destination
            ?: return null
        return TripState(destination = destination, committed = committed)
    }

    override suspend fun clearLegacyTrip() {
        dataStore.edit { preferences ->
            preferences.putJson<CommittedTrip>(COMMITTED_TRIP, null)
            val destinations = preferences.getJson<List<StoredDestination>>(DESTINATIONS)
            preferences.putJson(DESTINATIONS, destinations?.map { it.copy(current = false) })
        }
    }
}

@Serializable
private data class StoredDestination(
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Written by older builds for the destination that is now in the trip state. */
    val current: Boolean = false,
    val address: String? = null,
) {
    fun toDomain() = Destination(name, LatLon(lat, lon), address)
}
