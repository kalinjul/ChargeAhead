package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkPreferences
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
    fun `the count ignores stored keys that no longer name a network`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        // A key that no network carries any more must not be counted.
        settings.setNetworks(
            NetworkPreferences(
                onlyPreferred = true,
                preferredOperators = setOf("enbw", "ghost-network-dropped-long-ago"),
            ),
        )
        val vm = DrawerViewModel(settings, FixedNetworkRepository(listOf(Network("enbw", "EnBW", rank = 0))))

        val state = vm.uiState.await { it.preferredNetworkCount > 0 }
        assertEquals(1, state.preferredNetworkCount, "the ghost key must not inflate the count")
    }

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(5_000L) { first(matching) }
}
