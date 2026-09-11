package de.autoapp.shared.ui

import de.autoapp.shared.domain.NetworkCatalog
import de.autoapp.shared.domain.NetworkPreferences
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

    private fun settings() =
        TrackingSettingsStore(PersistentSettingsStore(InMemoryKeyValueStorage(), Dispatchers.Unconfined))

    @Test
    fun `toggling stages without touching settings`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        vm.onNetworkToggled("enbw")

        val state = vm.uiState.await { it.selected.isNotEmpty() }
        assertEquals(setOf("enbw"), state.selected)
        assertTrue(settings.saved.isEmpty(), "nothing persisted yet")
    }

    @Test
    fun `leaving the screen commits the staged selection`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        vm.onNetworkToggled("enbw")
        vm.uiState.await { it.selected == setOf("enbw") }
        vm.onLeave()

        assertEquals(setOf("enbw"), settings.saved.single().preferredOperators)
    }

    @Test
    fun `leaving without an edit writes nothing`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)
        vm.uiState.await { it.networks.isNotEmpty() }

        vm.onLeave()

        assertTrue(settings.saved.isEmpty(), "an untouched visit must not overwrite the stored selection")
    }

    @Test
    fun `toggling twice removes the network`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        vm.onNetworkToggled("tesla")
        vm.uiState.await { "tesla" in it.selected }
        vm.onNetworkToggled("tesla")

        val state = vm.uiState.await { "tesla" !in it.selected }
        assertFalse("tesla" in state.selected)
    }

    @Test
    fun `only preferred is on by default and survives a commit`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        assertTrue(vm.uiState.await { it.networks.isNotEmpty() }.onlyPreferred)

        vm.onOnlyPreferredChanged(false)
        val state = vm.uiState.await { !it.onlyPreferred }
        assertTrue(settings.saved.isEmpty(), "staged, like the ticks")
        assertFalse(state.onlyPreferred)

        vm.onLeave()
        assertFalse(settings.saved.single().onlyPreferred)
    }

    @Test
    fun `a second visit starts from what was stored`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        vm.onNetworkToggled("enbw")
        vm.uiState.await { it.selected == setOf("enbw") }
        vm.onLeave()
        // The ViewModel is activity-scoped, so the same instance is reused.
        vm.onLeave()

        assertEquals(1, settings.saved.size, "nothing left staged after the commit")
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

    @Test
    fun `without a search term the stored networks come first`() = runBlocking<Unit> {
        val settings = settings()
        // A network far down the catalog, so plain catalog order would not
        // put it on top by accident.
        val key = NetworkCatalog.all.last().key
        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf(key)))
        val vm = NetworksViewModel(settings)

        val state = vm.uiState.await { it.selected == setOf(key) }
        assertEquals(key, state.networks.first().key)
        assertEquals(NetworkCatalog.all.size, state.networks.size, "nothing dropped")
    }

    @Test
    fun `ticking a network does not reshuffle the open list`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)
        vm.onEnter()
        val key = NetworkCatalog.all.last().key

        val before = vm.uiState.await { it.networks.isNotEmpty() }.networks.map { it.key }
        vm.onNetworkToggled(key)
        val after = vm.uiState.await { key in it.selected }.networks.map { it.key }

        assertEquals(before, after, "the order is frozen while the screen is open")
    }

    @Test
    fun `the next visit floats the newly ticked network to the top`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)
        val key = NetworkCatalog.all.last().key

        vm.onEnter()
        vm.onNetworkToggled(key)
        vm.uiState.await { key in it.selected }
        vm.onLeave() // commit — the pick is stored now
        vm.onEnter() // reopening re-sorts from what was stored

        val state = vm.uiState.await { it.networks.first().key == key }
        assertEquals(key, state.networks.first().key)
        assertEquals(NetworkCatalog.all.size, state.networks.size, "nothing dropped")
    }

    @Test
    fun `unselected networks are alphabetical`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings)

        val names = vm.uiState.await { it.networks.isNotEmpty() }.networks
            .map { OperatorKey.folded(it.name) }
        assertEquals(names.sorted(), names, "nothing selected, so the whole list is alphabetical")
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
