package org.julakali.chargeahead.shared.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.FakeVehicleCatalog
import org.julakali.chargeahead.shared.domain.customVehicle
import org.julakali.chargeahead.shared.domain.usecases.EditVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.RemoveVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.RestoreCatalogValuesInteractor
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testPresets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Fahrzeug anpassen": one row per value, each edited on its own and applied or thrown away. */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleSettingsViewModelTest {

    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
    private val catalog = FakeVehicleCatalog()
    private val preset = testPresets.first()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private suspend fun viewModel(keep: Boolean = false): VehicleSettingsViewModel {
        if (!keep) vehicles.setVehicle(preset.toProfile())
        return VehicleSettingsViewModel(
            vehicles,
            catalog,
            EditVehicleInteractor(vehicles, catalog),
            RestoreCatalogValuesInteractor(vehicles, catalog),
            RemoveVehicleInteractor(vehicles),
        ).also { it.uiState.await { state -> state.vehicle != null } }
    }

    @Test
    fun `the name editor starts from the car's name and applies what was typed`() = runBlocking<Unit> {
        val viewModel = viewModel()

        viewModel.onNameEditOpened()
        assertEquals(VehicleEditor.Name(preset.name), viewModel.uiState.await { it.editor != null }.editor)
        viewModel.onEditorInputChanged("Familienkutsche")
        viewModel.onEditorConfirmed()

        assertNull(viewModel.uiState.await { it.editor == null }.editor)
        assertEquals("Familienkutsche", vehicles.vehicle.awaitValue { it?.displayName == "Familienkutsche" }?.displayName)
    }

    @Test
    fun `the battery editor takes the German decimal comma`() = runBlocking<Unit> {
        val viewModel = viewModel()

        viewModel.onBatteryEditOpened()
        viewModel.onEditorInputChanged("70,5")
        viewModel.onEditorConfirmed()

        assertEquals(70.5, vehicles.vehicle.awaitValue { it?.usableBatteryKwh == 70.5 }?.usableBatteryKwh)
    }

    @Test
    fun `a number that does not parse cannot be applied and keeps the editor open`() = runBlocking<Unit> {
        val viewModel = viewModel()

        viewModel.onDcPeakEditOpened()
        viewModel.onEditorInputChanged("viel")
        assertFalse(viewModel.uiState.await { it.editor == VehicleEditor.DcPeak("viel") }.editor!!.applicable)
        viewModel.onEditorConfirmed()

        assertEquals(VehicleEditor.DcPeak("viel"), viewModel.uiState.await { it.editor != null }.editor)
        assertEquals(preset.dcPeakPowerKw, vehicles.vehicle.first()?.dcPeakPowerKw)
    }

    @Test
    fun `dismissing an editor throws the change away`() = runBlocking<Unit> {
        val viewModel = viewModel()

        viewModel.onBatteryEditOpened()
        viewModel.onEditorInputChanged("12")
        viewModel.onEditorDismissed()

        assertNull(viewModel.uiState.await { it.editor == null }.editor)
        assertEquals(preset.usableBatteryKwh, vehicles.vehicle.first()?.usableBatteryKwh)
    }

    @Test
    fun `the consumption is typed, to the tenth, comma and all`() = runBlocking<Unit> {
        val viewModel = viewModel()
        assertEquals(preset, viewModel.uiState.await { it.catalog != null }.catalog)

        viewModel.onConsumptionEditOpened()
        assertEquals(VehicleEditor.Consumption(preset.consumptionKwhPer100Km.asLocalInput()), viewModel.uiState.await { it.editor != null }.editor)
        viewModel.onEditorInputChanged("21,3")
        viewModel.onEditorConfirmed()

        val stored = vehicles.vehicle.awaitValue { it?.consumptionKwhPer100Km == 21.3 }
        assertTrue(stored!!.ownConsumption)
    }

    @Test
    fun `typing the datasheet value follows the catalog again`() = runBlocking<Unit> {
        vehicles.setVehicle(preset.toProfile().copy(consumptionKwhPer100Km = 25.0, ownConsumption = true))
        val viewModel = viewModel(keep = true)

        viewModel.onConsumptionEditOpened()
        viewModel.uiState.await { it.editor != null }
        viewModel.onEditorInputChanged(preset.consumptionKwhPer100Km.asLocalInput())
        viewModel.onEditorConfirmed()

        val stored = vehicles.vehicle.awaitValue { it?.consumptionKwhPer100Km != 25.0 }!!
        assertEquals(preset.consumptionKwhPer100Km, stored.consumptionKwhPer100Km)
        assertFalse(stored.ownConsumption)
    }

    @Test
    fun `removing asks first, and only a yes removes`() = runBlocking<Unit> {
        val viewModel = viewModel()

        viewModel.onVehicleRemoveRequested()
        assertTrue(viewModel.uiState.await { it.confirmingRemoval }.confirmingRemoval)
        viewModel.onVehicleRemoveCancelled()

        assertFalse(viewModel.uiState.await { !it.confirmingRemoval }.confirmingRemoval)
        assertEquals(1, vehicles.vehicles.first().size)
    }

    @Test
    fun `only a catalog car has catalog values to go back to`() = runBlocking<Unit> {
        assertTrue(viewModel().uiState.await { it.vehicle != null }.catalog != null)

        vehicles.setVehicle(customVehicle("Eigenbau"))
        val own = VehicleSettingsViewModel(
            vehicles, catalog, EditVehicleInteractor(vehicles, catalog), RestoreCatalogValuesInteractor(vehicles, catalog), RemoveVehicleInteractor(vehicles),
        )

        assertFalse(own.uiState.await { it.vehicle?.displayName == "Eigenbau" }.catalog != null)
    }

    @Test
    fun `an own consumption alone offers the catalog values back, a new name doesn't`() = runBlocking<Unit> {
        val viewModel = viewModel()

        viewModel.onNameEditOpened()
        viewModel.uiState.await { it.editor != null }
        viewModel.onEditorInputChanged("Familienkutsche")
        viewModel.onEditorConfirmed()
        assertFalse(viewModel.uiState.await { it.vehicle?.displayName == "Familienkutsche" }.canRestoreCatalogValues)

        viewModel.onConsumptionEditOpened()
        viewModel.uiState.await { it.editor != null }
        viewModel.onEditorInputChanged((preset.consumptionKwhPer100Km + 2).asLocalInput())
        viewModel.onEditorConfirmed()

        assertTrue(viewModel.uiState.await { it.canRestoreCatalogValues }.canRestoreCatalogValues)
    }

    @Test
    fun `a changed catalog car offers its catalog values back`() = runBlocking<Unit> {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.await { it.vehicle != null }.canRestoreCatalogValues)

        viewModel.onBatteryEditOpened()
        viewModel.onEditorInputChanged("60")
        viewModel.onEditorConfirmed()
        viewModel.uiState.await { it.canRestoreCatalogValues }

        viewModel.onCatalogValuesRestored()

        assertEquals(preset.usableBatteryKwh, viewModel.uiState.await { !it.canRestoreCatalogValues }.vehicle?.usableBatteryKwh)
    }

    @Test
    fun `removing the car empties the page and the garage`() = runBlocking<Unit> {
        val viewModel = viewModel()

        viewModel.onVehicleRemoveRequested()
        viewModel.uiState.await { it.confirmingRemoval }
        viewModel.onVehicleRemoveConfirmed()

        assertTrue(viewModel.uiState.await { it.removed }.removed)
        assertTrue(vehicles.vehicles.first().isEmpty())
    }

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(5_000) { first(matching) }

    /** Same for a store flow: the writes behind it are asynchronous. */
    private suspend fun <T> Flow<T>.awaitValue(matching: (T) -> Boolean): T =
        withTimeout(5_000) { first(matching) }
}
