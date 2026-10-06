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
import org.julakali.chargeahead.shared.domain.usecases.GarageObserver
import org.julakali.chargeahead.shared.domain.usecases.SelectVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateArrivalSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
import org.julakali.chargeahead.shared.domain.DEFAULT_ASSUMED_SOC_PERCENT
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The garage page's own state: which car, and the arrival level's sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
class GarageViewModelTest {

    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
    private val viewModel by lazy {
        val catalog = FakeVehicleCatalog()
        GarageViewModel(
            vehicles,
            GarageObserver(vehicles, catalog),
            SelectVehicleInteractor(vehicles),
            UpdateArrivalSocInteractor(vehicles),
            UpdateManualSocInteractor(vehicles),
        )
    }

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the arrival sheet opens on the level in force and applies the dragged one`() = runBlocking<Unit> {
        vehicles.setArrivalSocPercent(20.0)
        viewModel.uiState.await { it.arrivalSocPercent == 20.0 }

        viewModel.onArrivalSheetOpened()
        assertEquals(20, viewModel.uiState.await { it.arrivalSheet != null }.arrivalSheet)
        viewModel.onArrivalSheetChanged(30)
        viewModel.onArrivalSheetConfirmed()

        assertNull(viewModel.uiState.await { it.arrivalSheet == null }.arrivalSheet)
        assertEquals(30.0, vehicles.arrivalSocPercent.awaitValue { it == 30.0 })
    }

    @Test
    fun `swiping the arrival sheet away keeps the level`() = runBlocking<Unit> {
        vehicles.setArrivalSocPercent(20.0)
        viewModel.uiState.await { it.arrivalSocPercent == 20.0 }

        viewModel.onArrivalSheetOpened()
        viewModel.uiState.await { it.arrivalSheet != null }
        viewModel.onArrivalSheetChanged(50)
        viewModel.onArrivalSheetDismissed()

        assertNull(viewModel.uiState.await { it.arrivalSheet == null }.arrivalSheet)
        assertEquals(20.0, vehicles.arrivalSocPercent.first())
    }

    @Test
    fun `the sheet stays inside what the planner can honour`() = runBlocking<Unit> {
        viewModel.uiState.await { true }
        viewModel.onArrivalSheetOpened()
        viewModel.uiState.await { it.arrivalSheet != null }

        viewModel.onArrivalSheetChanged(95)

        assertEquals(ARRIVAL_SOC_RANGE.last, viewModel.uiState.await { it.arrivalSheet != 10 }.arrivalSheet)
    }

    @Test
    fun `the garage loads before it claims there is no car`() {
        assertTrue(viewModel.uiState.value.loading)
    }

    @Test
    fun `without an entered level the garage shows the one planning assumes`() = runBlocking<Unit> {
        assertEquals(DEFAULT_ASSUMED_SOC_PERCENT, viewModel.uiState.await { !it.loading }.socPercent)
    }

    @Test
    fun `the battery level sheet opens on the stored level and applies the dragged one`() = runBlocking<Unit> {
        vehicles.setManualSocPercent(7.0)
        viewModel.uiState.await { it.socPercent == 7.0 }

        viewModel.onSocSheetOpened()
        assertEquals(7, viewModel.uiState.await { it.socSheet != null }.socSheet)
        viewModel.onSocSheetChanged(60)
        viewModel.onSocSheetConfirmed()

        assertNull(viewModel.uiState.await { it.socSheet == null }.socSheet)
        assertEquals(60.0, vehicles.manualSocPercent.awaitValue { it == 60.0 })
    }

    @Test
    fun `swiping the battery level sheet away keeps the level`() = runBlocking<Unit> {
        vehicles.setManualSocPercent(40.0)
        viewModel.uiState.await { it.socPercent == 40.0 }

        viewModel.onSocSheetOpened()
        viewModel.uiState.await { it.socSheet != null }
        viewModel.onSocSheetChanged(90)
        viewModel.onSocSheetDismissed()

        viewModel.uiState.await { it.socSheet == null }
        assertEquals(40.0, vehicles.manualSocPercent.first())
    }

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(5_000) { first(matching) }

    private suspend fun <T> Flow<T>.awaitValue(matching: (T) -> Boolean): T =
        withTimeout(5_000) { first(matching) }
}
