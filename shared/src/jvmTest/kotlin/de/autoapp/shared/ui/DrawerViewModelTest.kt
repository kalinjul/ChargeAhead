package de.autoapp.shared.ui

import de.autoapp.shared.domain.NetworkCatalog
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.settings.InMemoryKeyValueStorage
import de.autoapp.shared.settings.PersistentSettingsStore
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
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val real = NetworkCatalog.all.first().key
        // A key from an older catalog that no longer resolves — it can never
        // tick a pill, so it must not be counted either.
        settings.setNetworks(
            NetworkPreferences(
                onlyPreferred = true,
                preferredOperators = setOf(real, "ghost-network-dropped-long-ago"),
            ),
        )
        val vm = DrawerViewModel(settings)

        val state = vm.uiState.await { it.preferredNetworkCount > 0 }
        assertEquals(1, state.preferredNetworkCount, "the ghost key must not inflate the count")
    }

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(5_000L) { first(matching) }
}
