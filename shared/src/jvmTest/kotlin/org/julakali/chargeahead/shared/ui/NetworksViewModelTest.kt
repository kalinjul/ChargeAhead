package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.OperatorKey
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
        TrackingSettingsStore(PersistentSettingsStore(InMemoryPreferencesDataStore()))

    @Test
    fun `networks that dropped off the list show only while selected`() = runBlocking<Unit> {
        val settings = settings()
        val stored = listOf(
            Network("kaufland", "Kaufland", rank = 0),
            Network("enbw", "EnBW", rank = null),
            Network("ladenetz", "ladenetz.de", rank = null),
        )
        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("enbw")))
        val vm = NetworksViewModel(settings, FixedNetworkRepository(stored))

        val state = vm.uiState.await { it.networks.isNotEmpty() }
        assertEquals(setOf("kaufland", "enbw"), state.networks.map { it.key }.toSet())
    }

    @Test
    fun `a selected network the list never had shows under its key`() = runBlocking<Unit> {
        val settings = settings()
        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("stadtwerke-kiel")))
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))

        val state = vm.uiState.await { it.networks.isNotEmpty() }
        assertEquals("stadtwerke-kiel", state.networks.first().name, "selected, so on top")
        assertEquals(LISTED.size + 1, state.networks.size)

        vm.onNetworkToggled("stadtwerke-kiel")
        vm.onLeave()
        assertEquals(emptySet(), settings.saved.last().preferredOperators, "it can be unticked")
    }

    @Test
    fun `toggling stages without touching settings`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))

        vm.onNetworkToggled("enbw")

        val state = vm.uiState.await { it.selected.isNotEmpty() }
        assertEquals(setOf("enbw"), state.selected)
        assertTrue(settings.saved.isEmpty(), "nothing persisted yet")
    }

    @Test
    fun `leaving the screen commits the staged selection`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))

        vm.onNetworkToggled("enbw")
        vm.uiState.await { it.selected == setOf("enbw") }
        vm.onLeave()

        assertEquals(setOf("enbw"), settings.saved.single().preferredOperators)
    }

    @Test
    fun `leaving commits even though the ViewModel is cleared right after`() = runBlocking<Unit> {
        // Like DataStore, the write suspends while it hops to its own thread.
        val settings = SlowSettingsStore(PersistentSettingsStore(InMemoryPreferencesDataStore()))
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory {
            initializer { NetworksViewModel(settings, FixedNetworkRepository(LISTED)) }
        })[NetworksViewModel::class]

        vm.onNetworkToggled("enbw")
        vm.uiState.await { it.selected == setOf("enbw") }
        // Popping the screen's back-stack entry disposes the route and clears its ViewModel.
        vm.onLeave()
        store.clear()

        val stored = withTimeout(5_000L) { settings.networks.first { it.preferredOperators.isNotEmpty() } }
        assertEquals(setOf("enbw"), stored.preferredOperators)
    }

    @Test
    fun `leaving without an edit writes nothing`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))
        vm.uiState.await { it.networks.isNotEmpty() }

        vm.onLeave()

        assertTrue(settings.saved.isEmpty(), "an untouched visit must not overwrite the stored selection")
    }

    @Test
    fun `toggling twice removes the network`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))

        vm.onNetworkToggled("tesla")
        vm.uiState.await { "tesla" in it.selected }
        vm.onNetworkToggled("tesla")

        val state = vm.uiState.await { "tesla" !in it.selected }
        assertFalse("tesla" in state.selected)
    }

    @Test
    fun `only preferred is on by default and survives a commit`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))

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
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))

        vm.onNetworkToggled("enbw")
        vm.uiState.await { it.selected == setOf("enbw") }
        vm.onLeave()
        // A second leave, e.g. a recomposition, finds nothing staged.
        vm.onLeave()

        assertEquals(1, settings.saved.size, "nothing left staged after the commit")
    }

    @Test
    fun `search filters the catalog by folded name`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))

        vm.onSearchChanged("ionity")

        val state = vm.uiState.await { it.search == "ionity" }
        assertTrue(state.networks.isNotEmpty(), "expected at least one Ionity match")
        assertTrue(state.networks.all {
            OperatorKey.folded(it.name).contains(OperatorKey.folded("ionity"))
        })
        // The full list is larger
        assertTrue(state.networks.size < LISTED.size)
    }

    @Test
    fun `clearing search restores the full catalog`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))

        vm.onSearchChanged("ionity")
        vm.onSearchChanged("")

        val state = vm.uiState.await { it.networks.size == LISTED.size }
        assertEquals(LISTED.size, state.networks.size)
    }

    @Test
    fun `without a search term the stored networks come first`() = runBlocking<Unit> {
        val settings = settings()
        // A network late in the alphabet, so plain order would not put it on
        // top by accident.
        val key = LISTED.last().key
        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf(key)))
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))

        val state = vm.uiState.await { it.selected == setOf(key) }
        assertEquals(key, state.networks.first().key)
        assertEquals(LISTED.size, state.networks.size, "nothing dropped")
    }

    @Test
    fun `ticking a network does not reshuffle the open list`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))
        vm.onEnter()
        val key = LISTED.last().key

        val before = vm.uiState.await { it.networks.isNotEmpty() }.networks.map { it.key }
        vm.onNetworkToggled(key)
        val after = vm.uiState.await { key in it.selected }.networks.map { it.key }

        assertEquals(before, after, "the order is frozen while the screen is open")
    }

    @Test
    fun `clearing the search floats a network ticked while searching to the top`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))
        val network = LISTED.last()
        vm.onEnter()
        vm.uiState.await { it.networks.isNotEmpty() }

        vm.onSearchChanged(network.name)
        vm.uiState.await { it.search == network.name }
        vm.onNetworkToggled(network.key)
        vm.uiState.await { network.key in it.selected }
        vm.onSearchChanged("")

        val state = vm.uiState.await { it.networks.size == LISTED.size && it.networks.first().key == network.key }
        assertEquals(network.key, state.networks.first().key)
        assertEquals(LISTED.size, state.networks.size, "nothing dropped")
    }

    @Test
    fun `the next visit floats the newly ticked network to the top`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))
        val key = LISTED.last().key

        vm.onEnter()
        vm.onNetworkToggled(key)
        vm.uiState.await { key in it.selected }
        vm.onLeave() // commit — the pick is stored now
        vm.onEnter() // reopening re-sorts from what was stored

        val state = vm.uiState.await { it.networks.first().key == key }
        assertEquals(key, state.networks.first().key)
        assertEquals(LISTED.size, state.networks.size, "nothing dropped")
    }

    @Test
    fun `unselected networks are alphabetical`() = runBlocking<Unit> {
        val settings = settings()
        val vm = NetworksViewModel(settings, FixedNetworkRepository(LISTED))

        val names = vm.uiState.await { it.networks.isNotEmpty() }.networks
            .map { OperatorKey.folded(it.name) }
        assertEquals(names.sorted(), names, "nothing selected, so the whole list is alphabetical")
    }

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(5_000L) { first(matching) }
}

/** Writes the way DataStore does: only after a hop to another thread. */
private class SlowSettingsStore(
    private val delegate: PersistentSettingsStore,
) : org.julakali.chargeahead.shared.domain.SettingsStore by delegate {

    override suspend fun setNetworks(preferences: NetworkPreferences) = withContext(Dispatchers.IO) {
        delay(50)
        delegate.setNetworks(preferences)
    }
}

/** Wraps a real SettingsStore to record setNetworks calls for assertion. */
private class TrackingSettingsStore(
    private val delegate: PersistentSettingsStore,
) : org.julakali.chargeahead.shared.domain.SettingsStore by delegate {

    val saved = mutableListOf<NetworkPreferences>()

    override suspend fun setNetworks(preferences: NetworkPreferences) {
        saved += preferences
        delegate.setNetworks(preferences)
    }
}

private val LISTED = listOf(
    Network("enbw", "EnBW", rank = 0),
    Network("ionity", "Ionity", rank = 1),
    Network("tesla", "Tesla", rank = 2),
    Network("aral-pulse", "Aral pulse", rank = 3),
    Network("zapgrid", "ZapGrid", rank = 4),
)

internal class FixedNetworkRepository(networks: List<Network>) : NetworkRepository {
    override val networks: Flow<List<Network>> = flowOf(networks)
    override suspend fun refresh() = Unit
}
