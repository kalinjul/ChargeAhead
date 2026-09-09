package de.autoapp.shared.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.autoapp.shared.db.ChargeSiteDatabase
import de.autoapp.shared.db.TileCoverageEntity
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
        assertEquals(1L, dao.freshTileCount("ocm", "enbw", 1, 1, 1, 1, 0L))
        assertEquals(0L, dao.freshTileCount("ocm", "ionity", 2, 2, 2, 2, 0L))
        assertEquals(0L, dao.freshTileCount("ocm", "enbw", 3, 3, 3, 3, 0L))
    }
}
