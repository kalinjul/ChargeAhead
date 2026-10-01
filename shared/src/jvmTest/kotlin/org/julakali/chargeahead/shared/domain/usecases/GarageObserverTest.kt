package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleCatalog
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the garage shows about the selected car beyond its profile: range and the catalog's consumption. */
class GarageObserverTest {

    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())

    private fun observer() = GarageObserver(vehicles).also { it(GarageObserver.Params()) }

    @Test
    fun `no car, no numbers`() = runBlocking {
        val garage = withTimeout(5_000) { observer().flow.first() }

        assertNull(garage.selected)
        assertNull(garage.selectedFullRangeKm)
        assertNull(garage.selectedPresetConsumption)
    }

    @Test
    fun `a preset car reports its full range and the catalog consumption`() = runBlocking {
        val preset = VehicleCatalog.all.first()
        vehicles.setVehicle(preset.toProfile())

        val garage = withTimeout(5_000) { observer().flow.first { it.selected != null } }

        assertEquals(preset.consumptionKwhPer100Km, garage.selectedPresetConsumption)
        val expected = preset.toProfile().usableBatteryKwh / preset.consumptionKwhPer100Km * 100.0
        assertEquals(expected, garage.selectedFullRangeKm!!, 0.5)
    }

    @Test
    fun `a hand-typed car has a range but no catalog consumption`() = runBlocking {
        vehicles.setVehicle(VehicleProfile("Eigenbau", 60.0, 20.0, setOf(ConnectorType.CCS2)))

        val garage = withTimeout(5_000) { observer().flow.first { it.selected != null } }

        assertTrue(garage.selectedFullRangeKm!! > 0.0)
        assertNull(garage.selectedPresetConsumption)
    }
}
