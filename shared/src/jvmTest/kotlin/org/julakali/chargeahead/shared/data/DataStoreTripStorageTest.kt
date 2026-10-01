package org.julakali.chargeahead.shared.data

import androidx.datastore.preferences.core.stringPreferencesKey

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteSegment
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripState
import org.julakali.chargeahead.shared.persistenceJson
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.DataStoreDestinationHistory
import org.julakali.chargeahead.shared.settings.SettingsLegacyTripSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataStoreTripStorageTest {

    private val preferences = InMemoryPreferencesDataStore()
    private val storage = DataStoreTripStorage(preferences)

    @Test
    fun `the whole state survives the round trip unchanged`() = runBlocking {
        val state = TripState(destination = munich, planned = plan, committed = committed)

        storage.write(state)

        assertEquals(state, DataStoreTripStorage(preferences).read())
    }

    /** No mirror types: what is on disk is the domain types' own JSON. */
    @Test
    fun `the payload is the domain types' own json`() = runBlocking {
        val state = TripState(destination = munich, planned = plan, committed = committed)

        storage.write(state)

        assertEquals(persistenceJson.encodeToString(state), preferences.data.first()[stringPreferencesKey("trip.state")])
    }

    /** A connector type from a newer build reads as unknown instead of costing the trip. */
    @Test
    fun `an unknown connector type reads as unknown`() = runBlocking {
        val raw = persistenceJson.encodeToString(TripState(destination = munich, planned = plan))
            .replace("\"CCS2\"", "\"MCS\"")
        val stored = DataStoreTripStorage(InMemoryPreferencesDataStore(mapOf("trip.state" to raw)))

        val connector = stored.read()!!.planned!!.stops.single().site.connectors.single()
        assertEquals(ConnectorType.UNKNOWN, connector.type)
    }

    @Test
    fun `nothing stored means no state`() = runBlocking {
        assertNull(storage.read())
    }

    @Test
    fun `the newest state replaces the previous one`() = runBlocking {
        storage.write(TripState(destination = munich, planned = plan))

        storage.write(TripState(destination = munich, committed = committed))

        assertEquals(TripState(destination = munich, committed = committed), storage.read())
    }

    /** A payload from a build that shaped the trip differently must not break the launch. */
    @Test
    fun `a corrupt payload reads as no state`() = runBlocking {
        val corrupt = DataStoreTripStorage(InMemoryPreferencesDataStore(mapOf("trip.state" to """{"planned":{"route":{"points":[]}}}""")))

        assertNull(corrupt.read())
    }

    @Test
    fun `a trip kept among the settings moves over once`() = runBlocking {
        val settingsFile = InMemoryPreferencesDataStore(
            mapOf(
                "trip.committed" to Json.encodeToString(committed),
                "route.destinations" to """[{"name":"München","lat":48.137,"lon":11.575,"current":true}]""",
            ),
        )
        val legacy = SettingsLegacyTripSource(settingsFile)
        val migrating = DataStoreTripStorage(preferences, legacy = legacy)

        val expected = TripState(destination = Destination("München", LatLon(48.137, 11.575)), committed = committed)
        assertEquals(expected, migrating.read())
        assertEquals(expected, DataStoreTripStorage(preferences).read())
        assertNull(legacy.legacyTrip())
        assertEquals(listOf(Destination("München", LatLon(48.137, 11.575))), DataStoreDestinationHistory(settingsFile).recentDestinations.first())
    }

    @Test
    fun `settings without a trip leave the state empty`() = runBlocking {
        val settingsFile = InMemoryPreferencesDataStore()
        val history = DataStoreDestinationHistory(settingsFile)
        history.addRecentDestination(munich)

        assertNull(DataStoreTripStorage(preferences, legacy = SettingsLegacyTripSource(settingsFile)).read())
        assertTrue(history.recentDestinations.first().isNotEmpty())
    }

    private companion object {
        val munich = Destination("München", LatLon(48.137, 11.575), "Marienplatz 1, 80331 München")
        val kassel = ChargeSite(
            id = "bnetza:1141226",
            name = "Ionity Kassel",
            operator = "IONITY GmbH",
            operatorId = 42L,
            position = LatLon(51.31, 9.49),
            connectors = listOf(Connector(ConnectorType.CCS2, 350.0, 6)),
            address = Address("Am Rasthof 1", "34123", "Kassel"),
            sources = setOf("bnetza", "datex"),
            liveStatusId = "ionity:1234",
            networkKey = "ionity",
        )
        val plan = TripPlan(
            route = Route(
                points = listOf(LatLon(53.55, 9.99), kassel.position, munich.position),
                distanceKm = 776.0,
                durationMinutes = 470.0,
                segments = listOf(RouteSegment(fromKm = 0.0, distanceKm = 320.0, durationMinutes = 200.0)),
            ),
            destination = munich,
            stops = listOf(
                PlannedStop(
                    site = kassel,
                    kmFromStart = 320.0,
                    arrivalSocPercent = 18.0,
                    departureSocPercent = 80.0,
                    chargeKwh = 48.0,
                    chargeMinutes = 24.0,
                    etaMinutesFromStart = 220.0,
                    maxPowerKw = 350.0,
                    stopMinutes = 5.0,
                    savesMinutes = 12.0,
                ),
            ),
            driveMinutes = 470.0,
            chargeMinutes = 24.0,
            arrivalSocPercent = 22.0,
            stopMinutes = 5.0,
        )
        val committed = CommittedTrip(plan, startSocPercent = 62.0, committedAtEpochMillis = 1_700_000_000_000)
    }
}
