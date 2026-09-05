package de.autoapp.shared.settings

import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.VehicleProfile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentSettingsStoreTest {

    private val vehicle = VehicleProfile(
        displayName = "Testwagen",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 18.0,
        acceptedConnectors = setOf(ConnectorType.CCS2, ConnectorType.TYPE2),
    )

    @Test
    fun aNewStore_hasNeitherProfileNorChargeLevel() {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())

        assertNull(store.vehicle.value)
        assertNull(store.manualSocPercent.value)
    }

    @Test
    fun aSavedProfile_survivesARestart() = runBlocking {
        val storage = InMemoryKeyValueStorage()
        PersistentSettingsStore(storage).setVehicle(vehicle)

        // A new instance on the same storage = an app restart.
        assertEquals(vehicle, PersistentSettingsStore(storage).vehicle.value)
    }

    @Test
    fun aSavedChargeLevel_survivesARestart() = runBlocking {
        val storage = InMemoryKeyValueStorage()
        PersistentSettingsStore(storage).setManualSocPercent(64.0)

        assertEquals(64.0, PersistentSettingsStore(storage).manualSocPercent.value)
    }

    @Test
    fun deletingTheProfile_clearsTheStorage() = runBlocking {
        val storage = InMemoryKeyValueStorage()
        val store = PersistentSettingsStore(storage)
        store.setVehicle(vehicle)

        store.setVehicle(null)

        assertNull(store.vehicle.value)
        assertNull(PersistentSettingsStore(storage).vehicle.value)
    }

    @Test
    fun chargeLevelIsClampedBetweenZeroAndHundred() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())

        store.setManualSocPercent(140.0)
        assertEquals(100.0, store.manualSocPercent.value)

        store.setManualSocPercent(-5.0)
        assertEquals(0.0, store.manualSocPercent.value)
    }

    @Test
    fun aHalfProfileInStorage_countsAsNone() {
        // A vehicle with a battery but no consumption figure breaks the
        // range formula — better no profile at all than that.
        val batteryOnly = InMemoryKeyValueStorage(
            mapOf("vehicle.usableBatteryKwh" to "77.0", "vehicle.displayName" to "Halb"),
        )

        assertNull(PersistentSettingsStore(batteryOnly).vehicle.value)
    }

    @Test
    fun nonsensicalValuesInStorage_countAsNoProfile() {
        val broken = InMemoryKeyValueStorage(
            mapOf(
                "vehicle.usableBatteryKwh" to "keine Zahl",
                "vehicle.consumptionKwhPer100Km" to "18.0",
            ),
        )
        val zeroBattery = InMemoryKeyValueStorage(
            mapOf(
                "vehicle.usableBatteryKwh" to "0",
                "vehicle.consumptionKwhPer100Km" to "18.0",
            ),
        )

        assertNull(PersistentSettingsStore(broken).vehicle.value)
        assertNull(PersistentSettingsStore(zeroBattery).vehicle.value)
    }

    @Test
    fun anUnknownConnectorTypeInStorage_doesNotCostTheWholeProfile() = runBlocking {
        // Otherwise an older app version would lose the whole profile on a
        // rollback just because it contains a single new enum value.
        val storage = InMemoryKeyValueStorage(
            mapOf(
                "vehicle.usableBatteryKwh" to "77.0",
                "vehicle.consumptionKwhPer100Km" to "18.0",
                "vehicle.acceptedConnectors" to "CCS2,STECKER_AUS_DER_ZUKUNFT,TYPE2",
            ),
        )

        val loaded = PersistentSettingsStore(storage).vehicle.value

        assertEquals(setOf(ConnectorType.CCS2, ConnectorType.TYPE2), loaded?.acceptedConnectors)
    }

    @Test
    fun theConnectorListFullySurvives() = runBlocking {
        val storage = InMemoryKeyValueStorage()
        PersistentSettingsStore(storage).setVehicle(
            vehicle.copy(acceptedConnectors = setOf(ConnectorType.CCS2, ConnectorType.CHADEMO)),
        )

        assertEquals(
            setOf(ConnectorType.CCS2, ConnectorType.CHADEMO),
            PersistentSettingsStore(storage).vehicle.value?.acceptedConnectors,
        )
    }

    // --- Destination and destination history ---

    private val munich = Destination("München Hauptbahnhof", LatLon(48.1407, 11.5569))
    private val nuremberg = Destination("Nürnberg Hauptbahnhof", LatLon(49.4457, 11.0823))

    @Test
    fun withoutADestination_theCorridorApplies() {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())

        assertNull(store.destination.value)
        assertTrue(store.recentDestinations.value.isEmpty())
    }

    @Test
    fun aDestination_survivesARestart() = runBlocking {
        val storage = InMemoryKeyValueStorage()
        PersistentSettingsStore(storage).setDestination(munich)

        assertEquals(munich, PersistentSettingsStore(storage).destination.value)
    }

    @Test
    fun aDestinationEndsUpInHistory() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())

        store.setDestination(munich)
        store.setDestination(nuremberg)

        assertEquals(listOf(nuremberg, munich), store.recentDestinations.value)
    }

    @Test
    fun deletingADestination_keepsTheHistory() = runBlocking {
        // Otherwise the driver would have to retype it after every trip.
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setDestination(munich)

        store.setDestination(null)

        assertNull(store.destination.value)
        assertEquals(listOf(munich), store.recentDestinations.value)
    }

    @Test
    fun theSameDestinationTwice_appearsOnlyOnceInHistory() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())

        store.setDestination(munich)
        store.setDestination(nuremberg)
        store.setDestination(munich)

        assertEquals(listOf(munich, nuremberg), store.recentDestinations.value)
    }

    @Test
    fun theHistoryIsLimited() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())

        repeat(12) { i ->
            store.setDestination(Destination("Ziel $i", LatLon(48.0 + i * 0.1, 11.0)))
        }

        assertEquals(8, store.recentDestinations.value.size)
        assertEquals("Ziel 11", store.recentDestinations.value.first().name)
    }

    @Test
    fun aNameWithSpecialCharacters_survivesSaving() = runBlocking {
        // Place names contain commas, quotes, and line breaks — hence JSON
        // instead of a hand-rolled delimiter.
        val storage = InMemoryKeyValueStorage()
        val tricky = Destination("St. Peter-Ording, \"Nord\"; Zeile\nZwei", LatLon(54.3, 8.6))
        PersistentSettingsStore(storage).setDestination(tricky)

        assertEquals(tricky, PersistentSettingsStore(storage).destination.value)
    }

    @Test
    fun aBrokenHistory_doesNotCrashTheApp() {
        val broken = InMemoryKeyValueStorage(mapOf("route.destinations" to "{kein JSON"))

        val store = PersistentSettingsStore(broken)

        assertNull(store.destination.value)
        assertTrue(store.recentDestinations.value.isEmpty())
    }
}
