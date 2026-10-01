package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.domain.VehicleCatalog
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The add-car list: the catalog minus what is in the garage, narrowed by the query. */
class VehiclePresetsObserverTest {

    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())

    private fun observer(query: String) =
        VehiclePresetsObserver(vehicles, testDispatchers).also { it(VehiclePresetsObserver.Params(query)) }

    @Test
    fun `an empty query lists the whole catalog`() = runBlocking {
        val matches = withTimeout(5_000) { observer("").flow.first() }

        assertEquals(VehicleCatalog.all, matches)
    }

    @Test
    fun `the query matches names case-insensitively`() = runBlocking {
        val sample = VehicleCatalog.all.first()
        val needle = sample.name.take(4).uppercase()

        val matches = withTimeout(5_000) { observer(needle).flow.first() }

        assertTrue(matches.isNotEmpty())
        assertTrue(matches.all { it.name.contains(needle, ignoreCase = true) })
    }

    @Test
    fun `a car already in the garage is not offered again`() = runBlocking {
        val owned = VehicleCatalog.all.first()
        vehicles.setVehicle(owned.toProfile())

        val matches = withTimeout(5_000) { observer("").flow.first { owned !in it } }

        assertFalse(owned in matches)
        assertEquals(VehicleCatalog.all.size - 1, matches.size)
    }
}
