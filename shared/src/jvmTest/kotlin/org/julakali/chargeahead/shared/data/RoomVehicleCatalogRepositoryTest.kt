package org.julakali.chargeahead.shared.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.testPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomVehicleCatalogRepositoryTest {

    private var listed: List<VehiclePreset> = emptyList()
    private var failing = false
    private val repository = RoomVehicleCatalogRepository(
        { if (failing) error("backend down") else listed },
        createChargeSiteDatabase(DatabaseFactory(), Dispatchers.IO),
    )

    @Test
    fun `nothing is offered before the first sync`() = runBlocking {
        assertTrue(repository.presets.first().isEmpty())
    }

    @Test
    fun `the backend's list is stored in its order, connectors included`() = runBlocking {
        listed = testPresets.reversed()

        repository.refresh()

        assertEquals(testPresets.reversed(), repository.presets.first())
    }

    @Test
    fun `a model that dropped off the list is no longer offered`() = runBlocking {
        listed = testPresets
        repository.refresh()
        listed = testPresets.drop(1)

        repository.refresh()

        assertEquals(testPresets.drop(1), repository.presets.first())
    }

    @Test
    fun `an empty answer or a failure keeps the catalog from before`() = runBlocking {
        listed = testPresets
        repository.refresh()

        listed = emptyList()
        repository.refresh()
        failing = true
        runCatching { repository.refresh() }

        assertEquals(testPresets, repository.presets.first())
    }
}
