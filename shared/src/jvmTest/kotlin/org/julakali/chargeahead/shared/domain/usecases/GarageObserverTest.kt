package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.FakeVehicleCatalog
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the garage shows about the selected car beyond its profile: range and the catalog's consumption. */
class GarageObserverTest {

    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())

    private fun observer() = GarageObserver(vehicles, FakeVehicleCatalog()).also { it(GarageObserver.Params()) }

    @Test
    fun `no car, no numbers`() = runBlocking {
        val garage = withTimeout(5_000) { observer().flow.first() }

        assertNull(garage.selected)
        assertTrue(garage.fullRangeKm.isEmpty())
        assertNull(garage.selectedPresetConsumption)
    }

    @Test
    fun `a preset car reports its full range and the catalog consumption`() = runBlocking {
        val preset = testPresets.first()
        vehicles.setVehicle(preset.toProfile())

        val garage = withTimeout(5_000) { observer().flow.first { it.selected != null } }

        assertEquals(preset.consumptionKwhPer100Km, garage.selectedPresetConsumption)
        val expected = preset.toProfile().usableBatteryKwh / preset.consumptionKwhPer100Km * 100.0
        assertEquals(expected, garage.fullRangeKm.getValue(garage.selected!!.id), 0.5)
    }

    /** The cards next to the selected one show their own range. */
    @Test
    fun `every car in the garage reports its own full range`() = runBlocking {
        val small = VehicleProfile("Klein", 40.0, 16.0, setOf(ConnectorType.CCS2))
        val big = VehicleProfile("Groß", 100.0, 20.0, setOf(ConnectorType.CCS2))
        vehicles.setVehicle(small)
        vehicles.setVehicle(big)

        val garage = withTimeout(5_000) { observer().flow.first { it.vehicles.size == 2 } }

        assertEquals(250.0, garage.fullRangeKm.getValue(small.id), 0.5)
        assertEquals(500.0, garage.fullRangeKm.getValue(big.id), 0.5)
    }

    @Test
    fun `a renamed preset car still finds its model by id`() = runBlocking {
        val preset = testPresets.first()
        vehicles.setVehicle(preset.toProfile().copy(displayName = "Unser Kleiner"))

        val garage = withTimeout(5_000) { observer().flow.first { it.selected != null } }

        assertEquals(preset.consumptionKwhPer100Km, garage.selectedPresetConsumption)
    }

    @Test
    fun `a hand-typed car is not linked to a model of the same name`() = runBlocking {
        val preset = testPresets.first()
        vehicles.setVehicle(preset.toProfile().copy(modelId = null))

        val garage = withTimeout(5_000) { observer().flow.first { it.selected != null } }

        assertNull(garage.selectedPresetConsumption)
    }

    @Test
    fun `a hand-typed car has a range but no catalog consumption`() = runBlocking {
        vehicles.setVehicle(VehicleProfile("Eigenbau", 60.0, 20.0, setOf(ConnectorType.CCS2)))

        val garage = withTimeout(5_000) { observer().flow.first { it.selected != null } }

        assertTrue(garage.fullRangeKm.getValue(garage.selected!!.id) > 0.0)
        assertNull(garage.selectedPresetConsumption)
    }
}
