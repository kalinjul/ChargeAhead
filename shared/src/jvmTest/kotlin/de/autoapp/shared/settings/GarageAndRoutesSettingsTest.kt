package de.autoapp.shared.settings

import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.SavedRoute
import de.autoapp.shared.domain.VehicleProfile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GarageAndRoutesSettingsTest {

    private fun profile(name: String) = VehicleProfile(
        displayName = name,
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 19.5,
        acceptedConnectors = setOf(ConnectorType.CCS2),
        dcPeakPowerKw = 135.0,
    )

    @Test
    fun `selecting a vehicle adds it to the garage`() = runBlocking<Unit> {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setVehicle(profile("ID.4"))
        store.setVehicle(profile("Model 3"))

        assertEquals(listOf("ID.4", "Model 3"), store.vehicles.value.map { it.displayName })
        assertEquals("Model 3", store.vehicle.value?.displayName)
    }

    @Test
    fun `garage survives the process, selection included`() = runBlocking<Unit> {
        val storage = InMemoryKeyValueStorage()
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
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setVehicle(profile("ID.4"))
        store.setVehicle(profile("Model 3"))

        store.removeVehicle("Model 3")

        assertEquals("ID.4", store.vehicle.value?.displayName)
        assertEquals(1, store.vehicles.value.size)
    }

    @Test
    fun `removing the last vehicle leaves an honest nothing`() = runBlocking<Unit> {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setVehicle(profile("ID.4"))
        store.removeVehicle("ID.4")

        assertNull(store.vehicle.value)
        assertTrue(store.vehicles.value.isEmpty())
    }

    @Test
    fun `a pre-garage install keeps its single vehicle visible`() = runBlocking<Unit> {
        val storage = InMemoryKeyValueStorage()
        // Written by an app version that only knew the legacy keys.
        storage.putString("vehicle.displayName", "Alt-Auto")
        storage.putString("vehicle.usableBatteryKwh", "58.0")
        storage.putString("vehicle.consumptionKwhPer100Km", "16.0")

        val store = PersistentSettingsStore(storage)
        assertEquals(listOf("Alt-Auto"), store.vehicles.value.map { it.displayName })
    }

    @Test
    fun `filters and tariffs round-trip`() = runBlocking<Unit> {
        val storage = InMemoryKeyValueStorage()
        PersistentSettingsStore(storage).apply {
            setChargeFilters(ChargeFilters(minPowerKw = 300.0, maxPriceEuroPerKwh = 0.6, maxDistanceKm = 2.5))
            setActiveTariffIds(setOf("ionity-passport", "enbw-m"))
        }

        val reloaded = PersistentSettingsStore(storage)
        assertEquals(300.0, reloaded.chargeFilters.value.minPowerKw)
        assertEquals(2.5, reloaded.chargeFilters.value.maxDistanceKm)
        assertEquals(setOf("ionity-passport", "enbw-m"), reloaded.activeTariffIds.value)
    }

    @Test
    fun `saved routes keep order, rename and removal work`() = runBlocking<Unit> {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        val muenchen = SavedRoute("r1", "Amsterdam → München", Destination("München", LatLon(48.14, 11.58)), "660 km · 2 Stopps")
        val hamburg = SavedRoute("r2", "Oma in Hamburg", Destination("Hamburg", LatLon(53.55, 9.99)))

        store.saveRoute(muenchen)
        store.saveRoute(hamburg)
        assertEquals(listOf("r2", "r1"), store.savedRoutes.value.map { it.id })

        store.renameSavedRoute("r1", "Wiesn-Tour")
        assertEquals("Wiesn-Tour", store.savedRoutes.value.first { it.id == "r1" }.name)

        store.removeSavedRoute("r2")
        assertEquals(listOf("r1"), store.savedRoutes.value.map { it.id })
    }
}
