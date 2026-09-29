package org.julakali.chargeahead.shared.settings

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.VehicleProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoriesTest {

    private val vehicle = VehicleProfile(
        displayName = "Testwagen",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 18.0,
        acceptedConnectors = setOf(ConnectorType.CCS2, ConnectorType.TYPE2),
    )

    @Test
    fun aNewStore_hasNeitherProfileNorChargeLevel() = runBlocking {
        val store = DataStoreVehicleRepository(InMemoryPreferencesDataStore())

        assertNull(store.vehicle.first())
        assertNull(store.manualSocPercent.first())
    }

    @Test
    fun aSavedProfile_survivesARestart() = runBlocking {
        val storage = InMemoryPreferencesDataStore()
        DataStoreVehicleRepository(storage).setVehicle(vehicle)

        // A new instance on the same storage = an app restart.
        assertEquals(vehicle, DataStoreVehicleRepository(storage).vehicle.first())
    }

    @Test
    fun aSavedChargeLevel_survivesARestart() = runBlocking {
        val storage = InMemoryPreferencesDataStore()
        DataStoreVehicleRepository(storage).setManualSocPercent(64.0)

        assertEquals(64.0, DataStoreVehicleRepository(storage).manualSocPercent.first())
    }

    @Test
    fun deletingTheProfile_clearsTheStorage() = runBlocking {
        val storage = InMemoryPreferencesDataStore()
        val store = DataStoreVehicleRepository(storage)
        store.setVehicle(vehicle)

        store.setVehicle(null)

        assertNull(store.vehicle.first())
        assertNull(DataStoreVehicleRepository(storage).vehicle.first())
    }

    @Test
    fun chargeLevelIsClampedBetweenZeroAndHundred() = runBlocking {
        val store = DataStoreVehicleRepository(InMemoryPreferencesDataStore())

        store.setManualSocPercent(140.0)
        assertEquals(100.0, store.manualSocPercent.first())

        store.setManualSocPercent(-5.0)
        assertEquals(0.0, store.manualSocPercent.first())
    }

    @Test
    fun aHalfProfileInStorage_countsAsNone() = runBlocking {
        // A partial profile is treated as no profile.
        val batteryOnly = InMemoryPreferencesDataStore(
            mapOf("vehicle.usableBatteryKwh" to "77.0", "vehicle.displayName" to "Halb"),
        )

        assertNull(DataStoreVehicleRepository(batteryOnly).vehicle.first())
    }

    @Test
    fun nonsensicalValuesInStorage_countAsNoProfile() = runBlocking {
        val broken = InMemoryPreferencesDataStore(
            mapOf(
                "vehicle.usableBatteryKwh" to "keine Zahl",
                "vehicle.consumptionKwhPer100Km" to "18.0",
            ),
        )
        val zeroBattery = InMemoryPreferencesDataStore(
            mapOf(
                "vehicle.usableBatteryKwh" to "0",
                "vehicle.consumptionKwhPer100Km" to "18.0",
            ),
        )

        assertNull(DataStoreVehicleRepository(broken).vehicle.first())
        assertNull(DataStoreVehicleRepository(zeroBattery).vehicle.first())
    }

    @Test
    fun anUnknownConnectorTypeInStorage_doesNotCostTheWholeProfile() = runBlocking {
        // Unknown connector names are skipped.
        val storage = InMemoryPreferencesDataStore(
            mapOf(
                "vehicle.usableBatteryKwh" to "77.0",
                "vehicle.consumptionKwhPer100Km" to "18.0",
                "vehicle.acceptedConnectors" to "CCS2,STECKER_AUS_DER_ZUKUNFT,TYPE2",
            ),
        )

        val loaded = DataStoreVehicleRepository(storage).vehicle.first()

        assertEquals(setOf(ConnectorType.CCS2, ConnectorType.TYPE2), loaded?.acceptedConnectors)
    }

    @Test
    fun theConnectorListFullySurvives() = runBlocking {
        val storage = InMemoryPreferencesDataStore()
        DataStoreVehicleRepository(storage).setVehicle(
            vehicle.copy(acceptedConnectors = setOf(ConnectorType.CCS2, ConnectorType.CHADEMO)),
        )

        assertEquals(
            setOf(ConnectorType.CCS2, ConnectorType.CHADEMO),
            DataStoreVehicleRepository(storage).vehicle.first()?.acceptedConnectors,
        )
    }

    // --- Destination history ---

    private val munich = Destination("München Hauptbahnhof", LatLon(48.1407, 11.5569))
    private val nuremberg = Destination("Nürnberg Hauptbahnhof", LatLon(49.4457, 11.0823))

    @Test
    fun theHistoryStartsEmpty() = runBlocking {
        val store = DataStoreDestinationHistory(InMemoryPreferencesDataStore())

        assertTrue(store.recentDestinations.first().isEmpty())
    }

    @Test
    fun theHistory_survivesARestart() = runBlocking {
        val storage = InMemoryPreferencesDataStore()
        DataStoreDestinationHistory(storage).addRecentDestination(munich)

        assertEquals(listOf(munich), DataStoreDestinationHistory(storage).recentDestinations.first())
    }

    @Test
    fun theNewestDestinationComesFirst() = runBlocking {
        val store = DataStoreDestinationHistory(InMemoryPreferencesDataStore())

        store.addRecentDestination(munich)
        store.addRecentDestination(nuremberg)

        assertEquals(listOf(nuremberg, munich), store.recentDestinations.first())
    }

    @Test
    fun theSameDestinationTwice_appearsOnlyOnceInHistory() = runBlocking {
        val store = DataStoreDestinationHistory(InMemoryPreferencesDataStore())

        store.addRecentDestination(munich)
        store.addRecentDestination(nuremberg)
        store.addRecentDestination(munich)

        assertEquals(listOf(munich, nuremberg), store.recentDestinations.first())
    }

    @Test
    fun theHistoryIsLimited() = runBlocking {
        val store = DataStoreDestinationHistory(InMemoryPreferencesDataStore())

        repeat(12) { i ->
            store.addRecentDestination(Destination("Ziel $i", LatLon(48.0 + i * 0.1, 11.0)))
        }

        assertEquals(8, store.recentDestinations.first().size)
        assertEquals("Ziel 11", store.recentDestinations.first().first().name)
    }

    @Test
    fun aNameWithSpecialCharacters_survivesSaving() = runBlocking {
        // Place names may contain commas, quotes, and line breaks.
        val storage = InMemoryPreferencesDataStore()
        val tricky = Destination("St. Peter-Ording, \"Nord\"; Zeile\nZwei", LatLon(54.3, 8.6))
        DataStoreDestinationHistory(storage).addRecentDestination(tricky)

        assertEquals(listOf(tricky), DataStoreDestinationHistory(storage).recentDestinations.first())
    }

    @Test
    fun theAddress_survivesSavingInTheHistory() = runBlocking {
        val storage = InMemoryPreferencesDataStore()
        val club = Destination("Uebel und Gefährlich", LatLon(53.556, 9.968), "Feldstraße 66, 20359 Hamburg")
        DataStoreDestinationHistory(storage).addRecentDestination(club)

        assertEquals(listOf(club), DataStoreDestinationHistory(storage).recentDestinations.first())
    }

    // --- Network filter ---

    @Test
    fun aNewStore_hasTheNetworkFilterSwitchedOn() = runBlocking {
        // Default on, so a first selection takes effect immediately.
        val store = DataStorePreferencesRepository(InMemoryPreferencesDataStore())

        assertTrue(store.networks.first().onlyPreferred)
        assertFalse(store.networks.first().isActive)
    }

    @Test
    fun aSwitchedOffNetworkFilter_survivesARestart() = runBlocking {
        val storage = InMemoryPreferencesDataStore()
        DataStorePreferencesRepository(storage).setNetworks(NetworkPreferences(onlyPreferred = false))

        assertFalse(DataStorePreferencesRepository(storage).networks.first().onlyPreferred)
    }

    @Test
    fun aSelectionOnARenamedNetworkKey_isCarriedOver() = runBlocking {
        // An install that ticked EWE Go back when the catalog keyed it "ewe".
        val storage = InMemoryPreferencesDataStore(
            mapOf("networks.preferred" to """["ewe","enbw"]"""),
        )

        val store = DataStorePreferencesRepository(storage)

        assertEquals(setOf("ewe-go", "enbw"), store.networks.first().preferredOperators)
    }


    @Test
    fun slowMode_lastsForTheProcessOnly() = runBlocking {
        val storage = InMemoryPreferencesDataStore()
        val preferences = DataStorePreferencesRepository(storage)

        preferences.setChargeFilters(ChargeFilters(minPowerKw = 50.0, slowMode = true))

        assertTrue(preferences.chargeFilters.first().slowMode)
        assertEquals(ChargeFilters(minPowerKw = 50.0), DataStorePreferencesRepository(storage).chargeFilters.first())
    }

    @Test
    fun repositoriesOnOneFile_keepEachOthersValues() = runBlocking {
        val storage = InMemoryPreferencesDataStore()

        DataStoreVehicleRepository(storage).setVehicle(vehicle)
        DataStorePreferencesRepository(storage).setNetworks(NetworkPreferences(onlyPreferred = false))
        DataStoreDestinationHistory(storage).addRecentDestination(munich)

        assertEquals(vehicle, DataStoreVehicleRepository(storage).vehicle.first())
        assertFalse(DataStorePreferencesRepository(storage).networks.first().onlyPreferred)
        assertEquals(listOf(munich), DataStoreDestinationHistory(storage).recentDestinations.first())
    }
    @Test
    fun aBrokenHistory_doesNotCrashTheApp() = runBlocking {
        val broken = InMemoryPreferencesDataStore(mapOf("route.destinations" to "{kein JSON"))

        val store = DataStoreDestinationHistory(broken)

        assertTrue(store.recentDestinations.first().isEmpty())
    }
}
