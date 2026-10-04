package org.julakali.chargeahead.shared.settings

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleProfile
import kotlinx.coroutines.flow.first
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
        val store = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
        store.setVehicle(profile("ID.4"))
        store.setVehicle(profile("Model 3"))

        assertEquals(listOf("ID.4", "Model 3"), store.vehicles.first().map { it.displayName })
        assertEquals("Model 3", store.vehicle.first()?.displayName)
    }

    @Test
    fun `the model id survives a restart, in the garage and as the selected car`() = runBlocking<Unit> {
        val storage = InMemoryPreferencesDataStore()
        DataStoreVehicleRepository(storage).setVehicle(profile("ID.4").copy(modelId = "a-model-id"))
        DataStoreVehicleRepository(storage).setVehicle(profile("Eigenbau"))

        val reopened = DataStoreVehicleRepository(storage)
        assertEquals(listOf("a-model-id", null), reopened.vehicles.first().map { it.modelId })
        reopened.setVehicle(reopened.vehicles.first().first())
        assertEquals("a-model-id", DataStoreVehicleRepository(storage).vehicle.first()?.modelId)
    }

    @Test
    fun `re-selecting a vehicle keeps its place in the garage`() = runBlocking<Unit> {
        val store = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
        val id4 = profile("ID.4")
        store.setVehicle(id4)
        store.setVehicle(profile("Model 3"))

        // Picking the first car again must not shove it to the end.
        store.setVehicle(id4)

        assertEquals(listOf("ID.4", "Model 3"), store.vehicles.first().map { it.displayName })
        assertEquals("ID.4", store.vehicle.first()?.displayName)
    }

    @Test
    fun `editing a vehicle updates it in place`() = runBlocking<Unit> {
        val store = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
        val id4 = profile("ID.4")
        store.setVehicle(id4)
        store.setVehicle(profile("Model 3"))

        store.setVehicle(id4.copy(consumptionKwhPer100Km = 21.0))

        assertEquals(listOf("ID.4", "Model 3"), store.vehicles.first().map { it.displayName })
        assertEquals(21.0, store.vehicles.first().first { it.displayName == "ID.4" }.consumptionKwhPer100Km)
    }

    @Test
    fun `renaming a vehicle keeps it in its slot`() = runBlocking<Unit> {
        val store = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
        val eigenbau = profile("E")
        store.setVehicle(eigenbau)
        store.setVehicle(profile("Model 3"))

        store.setVehicle(eigenbau.copy(displayName = "Ei"))
        store.setVehicle(eigenbau.copy(displayName = "Eigenbau"))

        assertEquals(listOf("Eigenbau", "Model 3"), store.vehicles.first().map { it.displayName })
        assertEquals(eigenbau.id, store.vehicle.first()?.id)
    }

    @Test
    fun `two cars with the same name are two cars`() = runBlocking<Unit> {
        val store = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
        store.setVehicle(profile("ID.4"))
        store.setVehicle(profile("ID.4").copy(modelId = "id4"))

        assertEquals(2, store.vehicles.first().size)
    }

    @Test
    fun `garage survives the process - selection included`() = runBlocking<Unit> {
        val storage = InMemoryPreferencesDataStore()
        DataStoreVehicleRepository(storage).apply {
            setVehicle(profile("ID.4"))
            setVehicle(profile("Model 3"))
        }

        val reloaded = DataStoreVehicleRepository(storage)
        assertEquals(2, reloaded.vehicles.first().size)
        assertEquals("Model 3", reloaded.vehicle.first()?.displayName)
        assertEquals(135.0, reloaded.vehicle.first()?.dcPeakPowerKw)
    }

    @Test
    fun `removing the selected vehicle promotes the next one`() = runBlocking<Unit> {
        val store = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
        val model3 = profile("Model 3")
        store.setVehicle(profile("ID.4"))
        store.setVehicle(model3)

        store.removeVehicle(model3.id)

        assertEquals("ID.4", store.vehicle.first()?.displayName)
        assertEquals(1, store.vehicles.first().size)
    }

    @Test
    fun `removing the last vehicle leaves an honest nothing`() = runBlocking<Unit> {
        val store = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
        val id4 = profile("ID.4")
        store.setVehicle(id4)
        store.removeVehicle(id4.id)

        assertNull(store.vehicle.first())
        assertTrue(store.vehicles.first().isEmpty())
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

        val store = DataStoreVehicleRepository(storage)
        assertEquals(listOf("Alt-Auto"), store.vehicles.first().map { it.displayName })
    }

    @Test
    fun `cars stored without an id keep one stable id, in the garage and as the selection`() = runBlocking<Unit> {
        val storage = InMemoryPreferencesDataStore(
            mapOf(
                "vehicle.displayName" to "Alt-Auto",
                "vehicle.usableBatteryKwh" to "58.0",
                "vehicle.consumptionKwhPer100Km" to "16.0",
                "vehicle.garage" to """[{"name":"Erstes","batteryKwh":40.0,"consumption":15.0},""" +
                    """{"name":"Alt-Auto","batteryKwh":58.0,"consumption":16.0}]""",
            ),
        )
        val store = DataStoreVehicleRepository(storage)
        val selected = store.vehicle.first()!!

        store.setVehicle(selected.copy(consumptionKwhPer100Km = 17.0))

        assertEquals(listOf("Erstes", "Alt-Auto"), store.vehicles.first().map { it.displayName })
        assertEquals(17.0, store.vehicles.first().last().consumptionKwhPer100Km)
        assertEquals(store.vehicles.first().last().id, DataStoreVehicleRepository(storage).vehicle.first()?.id)
    }

    @Test
    fun `filters round-trip`() = runBlocking<Unit> {
        val storage = InMemoryPreferencesDataStore()
        DataStorePreferencesRepository(storage).apply {
            setChargeFilters(ChargeFilters(minPowerKw = 300.0))
        }

        val reloaded = DataStorePreferencesRepository(storage)
        assertEquals(300.0, reloaded.chargeFilters.first().minPowerKw)
    }
}
