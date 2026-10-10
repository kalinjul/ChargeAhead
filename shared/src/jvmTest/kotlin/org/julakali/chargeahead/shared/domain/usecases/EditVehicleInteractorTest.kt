package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.FakeVehicleCatalog
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.usecases.EditVehicleInteractor.Edit
import org.julakali.chargeahead.shared.testVehicleRepository
import org.julakali.chargeahead.shared.testPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One edit on the garage's detail page, applied to the selected car. */
class EditVehicleInteractorTest {

    private val vehicles = testVehicleRepository()
    private val edit = EditVehicleInteractor(vehicles, FakeVehicleCatalog())
    private val preset = testPresets.first()

    private suspend fun selected() = vehicles.vehicle.first()!!

    @Test
    fun `renaming a catalog car keeps the name its own, the values still follow the catalog`() = runBlocking {
        vehicles.setVehicle(preset.toProfile())

        edit(EditVehicleInteractor.Params(Edit.Name("  Familienkutsche ")))

        assertEquals("Familienkutsche", selected().displayName)
        assertTrue(selected().ownName)
        assertFalse(selected().customized)
        assertEquals(preset.id, selected().modelId)
    }

    @Test
    fun `a value set back to the catalog's makes the car follow the catalog again`() = runBlocking {
        vehicles.setVehicle(preset.toProfile())

        edit(EditVehicleInteractor.Params(Edit.Battery(preset.usableBatteryKwh - 5)))
        assertTrue(selected().customized)
        edit(EditVehicleInteractor.Params(Edit.Battery(preset.usableBatteryKwh)))

        assertFalse(selected().customized)
    }

    @Test
    fun `battery and charging power land on the car and keep its place in the garage`() = runBlocking {
        vehicles.setVehicle(preset.toProfile())
        val id = selected().id

        edit(EditVehicleInteractor.Params(Edit.Battery(70.5)))
        edit(EditVehicleInteractor.Params(Edit.DcPeak(135.0)))

        assertEquals(70.5, selected().usableBatteryKwh)
        assertEquals(135.0, selected().dcPeakPowerKw)
        assertEquals(id, selected().id)
        assertEquals(1, vehicles.vehicles.first().size)
    }

    @Test
    fun `a consumption of the driver's own is kept apart from the catalog values`() = runBlocking {
        vehicles.setVehicle(preset.toProfile())

        edit(EditVehicleInteractor.Params(Edit.Consumption(preset.consumptionKwhPer100Km + 2)))

        assertEquals(preset.consumptionKwhPer100Km + 2, selected().consumptionKwhPer100Km)
        assertTrue(selected().ownConsumption)
        assertFalse(selected().customized, "consumption alone leaves the rest following the catalog")
    }

    @Test
    fun `clicking back onto the datasheet value follows the catalog again`() = runBlocking {
        vehicles.setVehicle(preset.toProfile().copy(consumptionKwhPer100Km = 25.0, ownConsumption = true))

        edit(EditVehicleInteractor.Params(Edit.Consumption(preset.consumptionKwhPer100Km)))

        assertFalse(selected().ownConsumption)
    }

    @Test
    fun `a value a float's width off the datasheet lands exactly on it`() = runBlocking {
        vehicles.setVehicle(preset.toProfile().copy(consumptionKwhPer100Km = 25.0, ownConsumption = true))

        edit(EditVehicleInteractor.Params(Edit.Consumption(preset.consumptionKwhPer100Km.toFloat().toDouble())))

        assertEquals(preset.consumptionKwhPer100Km, selected().consumptionKwhPer100Km)
        assertFalse(selected().ownConsumption)
    }

    @Test
    fun `applying a sheet unchanged leaves a catalog car following the catalog`() = runBlocking {
        vehicles.setVehicle(preset.toProfile())

        edit(EditVehicleInteractor.Params(Edit.Name(preset.name)))

        assertFalse(selected().customized)
        assertFalse(selected().ownName)
    }

    @Test
    fun `a car typed in by hand is never marked as changed from the catalog`() = runBlocking {
        vehicles.setVehicle(VehicleProfile("Eigenbau", 50.0, 18.0, setOf(ConnectorType.CCS2)))

        edit(EditVehicleInteractor.Params(Edit.Battery(55.0)))

        assertFalse(selected().customized)
        assertNull(selected().modelId)
    }

    @Test
    fun `nothing selected, nothing written`() = runBlocking {
        edit(EditVehicleInteractor.Params(Edit.Name("Geist")))

        assertNull(vehicles.vehicle.first())
    }
}
