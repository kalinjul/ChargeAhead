package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.FakeVehicleCatalog
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testDispatchers
import org.julakali.chargeahead.shared.testPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The add-car list: the catalog minus what is in the garage, narrowed by the query. */
class VehiclePresetsObserverTest {

    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())

    private fun observer(query: String, catalog: FakeVehicleCatalog = FakeVehicleCatalog()) =
        VehiclePresetsObserver(catalog, vehicles, testDispatchers).also { it(VehiclePresetsObserver.Params(query)) }

    @Test
    fun `an empty query lists the whole catalog`() = runBlocking {
        val matches = withTimeout(5_000) { observer("").flow.first() }

        assertEquals(testPresets, matches)
    }

    @Test
    fun `the query matches names case-insensitively`() = runBlocking {
        val matches = withTimeout(5_000) { observer("fiat").flow.first() }

        assertEquals(listOf(testPresets.first { it.name.startsWith("Fiat") }), matches)
    }

    @Test
    fun `a car already in the garage is not offered again`() = runBlocking {
        val owned = testPresets.first()
        vehicles.setVehicle(owned.toProfile())

        val matches = withTimeout(5_000) { observer("").flow.first { owned !in it } }

        assertEquals(testPresets - owned, matches)
    }

    @Test
    fun `before the first sync there is nothing to pick`() = runBlocking {
        val matches = withTimeout(5_000) { observer("", FakeVehicleCatalog(emptyList())).flow.first() }

        assertTrue(matches.isEmpty())
    }
}
