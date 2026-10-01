package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.committedFirst
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

/** The network picker's catalog: what can be chosen, folded for search, narrowed by the query. */
class SelectableNetworksObserverTest {

    private val listed = listOf(
        Network(key = "ionity", name = "IONITY", rank = 1),
        Network(key = "enbw", name = "EnBW mobility+", rank = 2),
        Network(key = "aral", name = "Aral pulse", rank = 3),
    )
    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val repository = object : NetworkRepository {
        override val networks: Flow<List<Network>> = MutableStateFlow(listed)
        override suspend fun refresh() = Unit
    }

    private fun observer() = SelectableNetworksObserver(repository, preferences, testDispatchers)

    private suspend fun <T> Flow<T>.await(): T = withTimeout(5_000) { first() }

    @Test
    fun `without a query every listed network is offered, folded once`() = runBlocking {
        val observer = observer()
        observer(SelectableNetworksObserver.Params(query = ""))

        val rows = observer.flow.await()

        assertEquals(listOf("ionity", "enbw", "aral"), rows.map { it.network.key })
        assertEquals("enbw mobility+", rows[1].folded)
    }

    @Test
    fun `the query narrows by folded name`() = runBlocking {
        val observer = observer()
        observer(SelectableNetworksObserver.Params(query = " MOBILITY"))

        assertEquals(listOf("enbw"), observer.flow.await().map { it.network.key })
    }

    @Test
    fun `a selected network the list dropped stays selectable under its key`() = runBlocking {
        preferences.setNetworks(NetworkPreferences(preferredOperators = setOf("tesla")))
        val observer = observer()
        observer(SelectableNetworksObserver.Params(query = ""))

        assertEquals(listOf("ionity", "enbw", "aral", "tesla"), observer.flow.await().map { it.network.key })
    }

    @Test
    fun `committed picks sort first, then alphabetically`() = runBlocking {
        val observer = observer()
        observer(SelectableNetworksObserver.Params(query = ""))
        val rows = observer.flow.await()

        val ordered = rows.committedFirst(NetworkPreferences(preferredOperators = setOf("aral")))

        assertEquals(listOf("aral", "enbw", "ionity"), ordered.map { it.key })
    }
}
