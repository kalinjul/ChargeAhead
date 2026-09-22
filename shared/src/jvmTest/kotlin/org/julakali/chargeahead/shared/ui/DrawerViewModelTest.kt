package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.usecases.UpdateChargeFiltersInteractor
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
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
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        // Off the backend's list, but it still filters, so it counts.
        settings.setNetworks(
            NetworkPreferences(
                onlyPreferred = true,
                preferredOperators = setOf("enbw", "stadtwerke-kiel"),
            ),
        )
        val vm = DrawerViewModel(settings, UpdateChargeFiltersInteractor(settings))

        val state = vm.uiState.await { it.preferredNetworkCount > 0 }
        assertEquals(2, state.preferredNetworkCount)
    }

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(5_000L) { first(matching) }
}
