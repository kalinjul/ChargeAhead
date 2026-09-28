package org.julakali.chargeahead.shared.settings

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleProfile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GarageSettingsTest {

    private fun profile(name: String) = VehicleProfile(
        displayName = name,
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 19.5,
        acceptedConnectors = setOf(ConnectorType.CCS2),
        dcPeakPowerKw = 135.0,
    )

    @Test
    fun `selecting a vehicle adds it to the garage`() = runBlocking<Unit> {
        val store = PersistentSettingsStore(InMemoryPreferencesDataStore())
        store.setVehicle(profile("ID.4"))
        store.setVehicle(profile("Model 3"))

        assertEquals(listOf("ID.4", "Model 3"), store.vehicles.value.map { it.displayName })
        assertEquals("Model 3", store.vehicle.value?.displayName)
    }

    @Test
    fun `re-selecting a vehicle keeps its place in the garage`() = runBlocking<Unit> {
        val store = PersistentSettingsStore(InMemoryPreferencesDataStore())
        store.setVehicle(profile("ID.4"))
        store.setVehicle(profile("Model 3"))

        // Picking the first car again must not shove it to the end.
        store.setVehicle(profile("ID.4"))

        assertEquals(listOf("ID.4", "Model 3"), store.vehicles.value.map { it.displayName })
        assertEquals("ID.4", store.vehicle.value?.displayName)
    }

    @Test
    fun `editing a vehicle updates it in place`() = runBlocking<Unit> {
        val store = PersistentSettingsStore(InMemoryPreferencesDataStore())
        store.setVehicle(profile("ID.4"))
        store.setVehicle(profile("Model 3"))

        store.setVehicle(profile("ID.4").copy(consumptionKwhPer100Km = 21.0))

        assertEquals(listOf("ID.4", "Model 3"), store.vehicles.value.map { it.displayName })
        assertEquals(21.0, store.vehicles.value.first { it.displayName == "ID.4" }.consumptionKwhPer100Km)
    }

    @Test
    fun `garage survives the process - selection included`() = runBlocking<Unit> {
        val storage = InMemoryPreferencesDataStore()
        PersistentSettingsStore(storage).apply {
            setVehicle(profile("ID.4"))
            setVehicle(profile("Model 3"))
        }

        val reloaded = PersistentSettingsStore(storage)
        assertEquals(2, reloaded.vehicles.value.size)
        assertEquals("Model 3", reloaded.vehicle.value?.displayName)
        assertEquals(135.0, reloaded.vehicle.value?.dcPeakPowerKw)
    }

    @Test
    fun `removing the selected vehicle promotes the next one`() = runBlocking<Unit> {
        val store = PersistentSettingsStore(InMemoryPreferencesDataStore())
        store.setVehicle(profile("ID.4"))
        store.setVehicle(profile("Model 3"))

        store.removeVehicle("Model 3")

        assertEquals("ID.4", store.vehicle.value?.displayName)
        assertEquals(1, store.vehicles.value.size)
    }

    @Test
    fun `removing the last vehicle leaves an honest nothing`() = runBlocking<Unit> {
        val store = PersistentSettingsStore(InMemoryPreferencesDataStore())
        store.setVehicle(profile("ID.4"))
        store.removeVehicle("ID.4")

        assertNull(store.vehicle.value)
        assertTrue(store.vehicles.value.isEmpty())
    }

    @Test
    fun `a pre-garage install keeps its single vehicle visible`() = runBlocking<Unit> {
        // Written by an app version that only knew the legacy keys.
        val storage = InMemoryPreferencesDataStore(
            mapOf(
                "vehicle.displayName" to "Alt-Auto",
                "vehicle.usableBatteryKwh" to "58.0",
                "vehicle.consumptionKwhPer100Km" to "16.0",
            ),
        )

        val store = PersistentSettingsStore(storage)
        assertEquals(listOf("Alt-Auto"), store.vehicles.value.map { it.displayName })
    }

    @Test
    fun `filters round-trip`() = runBlocking<Unit> {
        val storage = InMemoryPreferencesDataStore()
        PersistentSettingsStore(storage).apply {
            setChargeFilters(ChargeFilters(minPowerKw = 300.0, maxDistanceKm = 2.5))
        }

        val reloaded = PersistentSettingsStore(storage)
        assertEquals(300.0, reloaded.chargeFilters.value.minPowerKw)
        assertEquals(2.5, reloaded.chargeFilters.value.maxDistanceKm)
    }
}
