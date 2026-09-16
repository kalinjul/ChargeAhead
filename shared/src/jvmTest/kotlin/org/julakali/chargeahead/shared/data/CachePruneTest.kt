package org.julakali.chargeahead.shared.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.db.CorridorCoverageEntity
import org.julakali.chargeahead.shared.db.TileCoverageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CachePruneTest {

    private val db = Room.inMemoryDatabaseBuilder<ChargeSiteDatabase>()
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()

    @Test fun prune_drops_stale_and_deselected() = runTest {
        val dao = db.chargeSites()
        dao.markTilesFetched(listOf(
            TileCoverageEntity("ocm", "enbw", 1, 1, 10_000L),      // fresh, selected → keep
            TileCoverageEntity("ocm", "ionity", 2, 2, 10_000L),    // fresh, deselected → drop
            TileCoverageEntity("ocm", "enbw", 3, 3, 1L),           // stale → drop
        ))
        pruneCache(db, selectedKeys = setOf("enbw"), now = 10_000L, ttlMillis = 5_000L)
        assertEquals(1, dao.freshTilesIn("ocm", "enbw", 1, 1, 1, 1, 0L).size)
        assertEquals(0, dao.freshTilesIn("ocm", "ionity", 2, 2, 2, 2, 0L).size)
        assertEquals(0, dao.freshTilesIn("ocm", "enbw", 3, 3, 3, 3, 0L).size)
    }

    @Test fun prune_drops_stale_and_deselected_corridors() = runTest {
        val dao = db.chargeSites()
        fun corridor(key: String, fetchedAt: Long) =
            CorridorCoverageEntity(0, "ocm", key, "48.0,11.0;48.5,11.0", 2.0, 48.0, 10.9, 48.5, 11.1, fetchedAt)
        dao.markCorridorsFetched(listOf(corridor("enbw", 10_000L), corridor("ionity", 10_000L), corridor("enbw", 1L)))

        pruneCache(db, selectedKeys = setOf("enbw"), now = 10_000L, ttlMillis = 5_000L)

        assertEquals(1, dao.freshCorridorsIn("ocm", "enbw", 47.0, 10.0, 49.0, 12.0, 0L).size)
        assertEquals(0, dao.freshCorridorsIn("ocm", "ionity", 47.0, 10.0, 49.0, 12.0, 0L).size)
    }
}
