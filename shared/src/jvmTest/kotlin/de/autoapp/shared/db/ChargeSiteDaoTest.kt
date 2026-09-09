package de.autoapp.shared.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChargeSiteDaoTest {

    @Test
    fun coverage_is_per_network() = runTest {
        val db = Room.inMemoryDatabaseBuilder<ChargeSiteDatabase>()
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        val dao = db.chargeSites()
        dao.markTilesFetched(listOf(TileCoverageEntity("ocm", "enbw", 481, 115, 1_000L)))
        assertEquals(1L, dao.freshTileCount("ocm", "enbw", 481, 481, 115, 115, 0L))
        assertEquals(0L, dao.freshTileCount("ocm", "ionity", 481, 481, 115, 115, 0L))
    }
}
