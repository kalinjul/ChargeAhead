package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.db.GarageVehicleEntity
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testDatabase
import org.julakali.chargeahead.shared.testVehicleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomVehicleRepositoryTest {

    private val storage = InMemoryPreferencesDataStore()
    private val database = testDatabase()

    private fun reopened() = testVehicleRepository(storage, database)

    private fun profile(name: String) = VehicleProfile(
        displayName = name,
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 19.5,
        acceptedConnectors = setOf(ConnectorType.CCS2),
        dcPeakPowerKw = 135.0,
    )

    @Test
    fun `selecting a vehicle adds it to the garage`() = runBlocking<Unit> {
        val store = testVehicleRepository()
        store.setVehicle(profile("ID.4"))
        store.setVehicle(profile("Model 3"))

        assertEquals(listOf("ID.4", "Model 3"), store.vehicles.first().map { it.displayName })
        assertEquals("Model 3", store.vehicle.first()?.displayName)
    }

    @Test
    fun `the model id survives a restart, in the garage and as the selected car`() = runBlocking<Unit> {
        reopened().setVehicle(profile("ID.4").copy(modelId = "a-model-id"))
        reopened().setVehicle(profile("Eigenbau"))

        val reopened = reopened()
        assertEquals(listOf("a-model-id", null), reopened.vehicles.first().map { it.modelId })
        reopened.setVehicle(reopened.vehicles.first().first())
        assertEquals("a-model-id", reopened().vehicle.first()?.modelId)
    }

    @Test
    fun `an own name survives a restart, in the garage and as the selected car`() = runBlocking<Unit> {
        reopened().setVehicle(profile("Familienkutsche").copy(modelId = "a-model-id", ownName = true))

        val reopened = reopened()
        assertTrue(reopened.vehicles.first().single().ownName)
        assertTrue(reopened.vehicle.first()!!.ownName)
    }

    @Test
    fun `re-selecting a vehicle keeps its place in the garage`() = runBlocking<Unit> {
        val store = testVehicleRepository()
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
        val store = testVehicleRepository()
        val id4 = profile("ID.4")
        store.setVehicle(id4)
        store.setVehicle(profile("Model 3"))

        store.setVehicle(id4.copy(consumptionKwhPer100Km = 21.0))

        assertEquals(listOf("ID.4", "Model 3"), store.vehicles.first().map { it.displayName })
        assertEquals(21.0, store.vehicles.first().first { it.displayName == "ID.4" }.consumptionKwhPer100Km)
    }

    @Test
    fun `renaming a vehicle keeps it in its slot`() = runBlocking<Unit> {
        val store = testVehicleRepository()
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
        val store = testVehicleRepository()
        store.setVehicle(profile("ID.4"))
        store.setVehicle(profile("ID.4").copy(modelId = "id4"))

        assertEquals(2, store.vehicles.first().size)
    }

    @Test
    fun `garage survives the process - selection included`() = runBlocking<Unit> {
        reopened().apply {
            setVehicle(profile("ID.4"))
            setVehicle(profile("Model 3"))
        }

        val reloaded = reopened()
        assertEquals(2, reloaded.vehicles.first().size)
        assertEquals("Model 3", reloaded.vehicle.first()?.displayName)
        assertEquals(135.0, reloaded.vehicle.first()?.dcPeakPowerKw)
    }

    @Test
    fun `removing the selected vehicle promotes the next one`() = runBlocking<Unit> {
        val store = testVehicleRepository()
        val model3 = profile("Model 3")
        store.setVehicle(profile("ID.4"))
        store.setVehicle(model3)

        store.removeVehicle(model3.id)

        assertEquals("ID.4", store.vehicle.first()?.displayName)
        assertEquals(1, store.vehicles.first().size)
    }

    @Test
    fun `only the selected car's id is written to the settings`() = runBlocking<Unit> {
        val id4 = profile("ID.4")
        reopened().setVehicle(id4)

        assertEquals(mapOf("vehicle.id" to id4.id), storage.data.first().asMap().entries.associate { it.key.name to it.value })
    }

    @Test
    fun `an unknown connector name in the database does not cost the car`() = runBlocking<Unit> {
        database.garageVehicles().upsert(
            listOf(
                GarageVehicleEntity(
                    id = "a", displayName = "ID.4", usableBatteryKwh = 77.0, consumptionKwhPer100Km = 18.0,
                    connectors = setOf("CCS2", "STECKER_AUS_DER_ZUKUNFT", "TYPE2"), dcPeakPowerKw = null,
                    modelId = null, customized = false, ownConsumption = false, ownName = false,
                ),
            ),
        )

        assertEquals(setOf(ConnectorType.CCS2, ConnectorType.TYPE2), reopened().vehicles.first().single().acceptedConnectors)
    }

    @Test
    fun `removing the last vehicle leaves an honest nothing`() = runBlocking<Unit> {
        val store = testVehicleRepository()
        val id4 = profile("ID.4")
        store.setVehicle(id4)
        store.removeVehicle(id4.id)

        assertNull(store.vehicle.first())
        assertTrue(store.vehicles.first().isEmpty())
    }
}
