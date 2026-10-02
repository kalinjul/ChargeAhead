package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.usecases.UpdateChargeFiltersInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateNetworksInteractor
import org.julakali.chargeahead.shared.domain.usecases.SetChargeModeInteractor
import org.julakali.chargeahead.shared.domain.ChargeMode
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DrawerViewModelTest {

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the count includes selected keys the network list does not know`() = runBlocking<Unit> {
        val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
        // Off the backend's list, but it still filters, so it counts.
        preferences.setNetworks(
            NetworkPreferences(
                onlyPreferred = true,
                preferredOperators = setOf("enbw", "stadtwerke-kiel"),
            ),
        )
        val state = viewModel(preferences).uiState.await { it.preferredNetworkCount > 0 }
        assertEquals(2, state.preferredNetworkCount)
    }

    @Test
    fun `the state carries the mode that is on`() = runBlocking<Unit> {
        val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
        preferences.setNetworks(NetworkPreferences(onlyPreferred = false))

        assertEquals(ChargeMode.BROWSE, viewModel(preferences).uiState.await { it.mode != ChargeMode.NORMAL }.mode)
    }

    @Test
    fun `selecting a mode stores it, selecting normal takes it back`() = runBlocking<Unit> {
        val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
        val vm = viewModel(preferences)

        vm.onModeSelected(ChargeMode.AC)
        assertEquals(ChargeMode.AC, vm.uiState.await { it.mode == ChargeMode.AC }.mode)

        vm.onModeSelected(ChargeMode.NORMAL)
        assertEquals(ChargeMode.NORMAL, vm.uiState.await { it.mode == ChargeMode.NORMAL }.mode)
    }

    private fun viewModel(preferences: DataStorePreferencesRepository) = DrawerViewModel(
        DataStoreVehicleRepository(InMemoryPreferencesDataStore()),
        preferences,
        UpdateChargeFiltersInteractor(preferences),
        SetChargeModeInteractor(preferences, UpdateChargeFiltersInteractor(preferences), UpdateNetworksInteractor(preferences)),
    )

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(5_000L) { first(matching) }
}
