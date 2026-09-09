package de.autoapp.shared.ui

import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.NetworkCatalog
import de.autoapp.shared.domain.OperatorKey
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NetworksViewModelTest {

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun settings() = TrackingSettingsStore(PersistentSettingsStore(InMemoryKeyValueStorage()))

    @Test
    fun `toggling stages without touching settings`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        vm.onNetworkToggled("enbw")

        val state = vm.uiState.await { it.pending.isNotEmpty() }
        assertEquals(setOf("enbw"), state.pending)
        assertTrue(settings.saved.isEmpty(), "nothing persisted yet")
        assertTrue(state.canConfirm)
    }

    @Test
    fun `confirm commits and disables until next change`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        vm.onNetworkToggled("enbw")
        vm.onConfirm()

        val state = vm.uiState.await { !it.canConfirm }
        assertEquals(setOf("enbw"), settings.saved.single().preferredOperators)
        assertFalse(state.canConfirm, "pending == committed now")
    }

    @Test
    fun `toggling twice removes the network`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        vm.onNetworkToggled("tesla")
        vm.onNetworkToggled("tesla")

        val state = vm.uiState.await { it.search == "" }
        assertFalse("tesla" in state.pending)
        assertFalse(state.canConfirm)
    }

    @Test
    fun `search filters the catalog by folded name`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        vm.onSearchChanged("ionity")

        val state = vm.uiState.await { it.search == "ionity" }
        assertTrue(state.networks.isNotEmpty(), "expected at least one Ionity match")
        assertTrue(state.networks.all {
            OperatorKey.folded(it.name).contains(OperatorKey.folded("ionity"))
        })
        // Full catalog is larger
        assertTrue(state.networks.size < NetworkCatalog.all.size)
    }

    @Test
    fun `clearing search restores the full catalog`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        vm.onSearchChanged("ionity")
        vm.onSearchChanged("")

        val state = vm.uiState.await { it.networks.size == NetworkCatalog.all.size }
        assertEquals(NetworkCatalog.all.size, state.networks.size)
    }

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(5_000L) { first(matching) }
}

/** Wraps a real SettingsStore to record setNetworks calls for assertion. */
private class TrackingSettingsStore(
    private val delegate: PersistentSettingsStore,
) : de.autoapp.shared.domain.SettingsStore by delegate {

    val saved = mutableListOf<NetworkPreferences>()

    override suspend fun setNetworks(preferences: NetworkPreferences) {
        saved += preferences
        delegate.setNetworks(preferences)
    }
}
