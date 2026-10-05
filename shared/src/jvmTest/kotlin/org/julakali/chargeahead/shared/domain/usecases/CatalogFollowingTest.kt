package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.FakeVehicleCatalog
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.invoke
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testDispatchers
import org.julakali.chargeahead.shared.testPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Catalog cars follow the catalog until the driver changes their values. */
class CatalogFollowingTest {

    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
    private val original = testPresets.first()
    private val revised = original.copy(name = "Fiat 500e (2024)", usableBatteryKwh = 38.0, consumptionKwhPer100Km = 14.2)
    private val catalog = FakeVehicleCatalog(refreshed = listOf(revised) + testPresets.drop(1))

    private suspend fun refresh() = RefreshVehicleCatalogInteractor(catalog, vehicles)().getOrThrow()

    @Test
    fun `an unchanged catalog car takes the new values, name included`() = runBlocking {
        val added = original.toProfile()
        vehicles.setVehicle(added)

        refresh()

        val expected = revised.toProfile().copy(id = added.id)
        assertEquals(listOf(expected), vehicles.vehicles.first())
        assertEquals(expected, vehicles.vehicle.first())
    }

    @Test
    fun `a customized catalog car keeps its values`() = runBlocking {
        val customized = original.toProfile().copy(usableBatteryKwh = 35.0, customized = true)
        vehicles.setVehicle(customized)

        refresh()

        assertEquals(customized, vehicles.vehicle.first())
    }

    @Test
    fun `the driver's own consumption survives an update`() = runBlocking {
        vehicles.setVehicle(original.toProfile().copy(consumptionKwhPer100Km = 18.5, ownConsumption = true))

        refresh()

        val updated = vehicles.vehicle.first()!!
        assertEquals(revised.name, updated.displayName)
        assertEquals(revised.usableBatteryKwh, updated.usableBatteryKwh)
        assertEquals(18.5, updated.consumptionKwhPer100Km)
    }

    @Test
    fun `the driver's own name survives an update, the values still follow`() = runBlocking {
        vehicles.setVehicle(original.toProfile().copy(displayName = "Familienkutsche", ownName = true))

        refresh()

        val updated = vehicles.vehicle.first()!!
        assertEquals("Familienkutsche", updated.displayName)
        assertEquals(revised.usableBatteryKwh, updated.usableBatteryKwh)
    }

    @Test
    fun `a car typed in by hand is left alone`() = runBlocking {
        val manual = VehicleProfile("Fiat 500e 42 kWh", 30.0, 16.0, setOf(ConnectorType.CCS2))
        val added = original.toProfile()
        vehicles.setVehicle(added)
        vehicles.setVehicle(manual)

        refresh()

        assertEquals(listOf(revised.toProfile().copy(id = added.id), manual), vehicles.vehicles.first())
        assertEquals(manual, vehicles.vehicle.first())
    }

    @Test
    fun `restoring catalog values drops the driver's values and keeps the name`() = runBlocking {
        val mine = original.toProfile().copy(
            displayName = "Meiner", ownName = true,
            usableBatteryKwh = 35.0, customized = true,
            consumptionKwhPer100Km = 25.0, ownConsumption = true,
        )
        vehicles.setVehicle(mine)

        RestoreCatalogValuesInteractor(vehicles, FakeVehicleCatalog())().getOrThrow()

        val expected = original.toProfile().copy(id = mine.id, displayName = "Meiner", ownName = true)
        assertEquals(listOf(expected), vehicles.vehicles.first())
        assertFalse(vehicles.vehicle.first()!!.customized)
    }

    @Test
    fun `a renamed catalog car is still not offered again`() = runBlocking {
        vehicles.setVehicle(original.toProfile())
        refresh()

        val observer = VehiclePresetsObserver(catalog, vehicles, testDispatchers).also { it(VehiclePresetsObserver.Params("")) }
        val matches = withTimeout(5_000) { observer.flow.first() }

        assertEquals(testPresets.drop(1), matches)
    }
}
