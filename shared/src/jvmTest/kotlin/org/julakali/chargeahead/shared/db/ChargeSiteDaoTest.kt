package org.julakali.chargeahead.shared.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType

class ChargeSiteDaoTest {

    @Test
    fun coverage_is_per_network() = runTest {
        val db = Room.inMemoryDatabaseBuilder<ChargeSiteDatabase>()
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        val dao = db.chargeSites()
        dao.markTilesFetched(listOf(TileCoverageEntity("ocm", "enbw", 481, 115, 1_000L)))
        assertEquals(listOf(TileIndex(481, 115)), dao.freshTilesIn("ocm", "enbw", 481, 481, 115, 115, 0L))
        assertEquals(emptyList(), dao.freshTilesIn("ocm", "ionity", 481, 481, 115, 115, 0L))
    }

    private fun site(id: String, lat: Double, lon: Double) = ChargeSiteEntity(
        id = id, sourceId = "ocm", name = id, operator = "Ionity", lat = lat, lon = lon,
        connectors = listOf(Connector(ConnectorType.CCS2, 300.0, 4)),
        street = null, postalCode = null, town = null, fetchedAtMillis = 0L,
        maxPowerKw = 300.0, maxDcPowerKw = 300.0, networkKey = "ionity",
    )

    /** The sheet shows a handful; the query hands over the nearest few, nearest first, and no more. */
    @Test
    fun nearest_sites_are_ordered_and_capped() = runTest {
        val db = Room.inMemoryDatabaseBuilder<ChargeSiteDatabase>()
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        val dao = db.chargeSites()
        // Latitude steps count 1.6x what longitude steps do up here; the scale has to undo that.
        dao.upsertSites(listOf(site("east2", 48.0, 11.02), site("north1", 48.009, 11.0), site("east3", 48.0, 11.03), site("north4", 48.036, 11.0)))

        val nearest = dao.observeNearestFilteredSitesInBox(
            south = 47.0, north = 49.0, west = 10.0, east = 12.0,
            slowMode = false, slowBelowKw = 50.0, minPowerKw = 50.0, filterNetworks = false, networkKeys = emptyList(),
            lat = 48.0, lon = 11.0, lonScale = 0.45, limit = 3,
        ).first()

        assertEquals(listOf("north1", "east2", "east3"), nearest.map { it.id })
    }
}
