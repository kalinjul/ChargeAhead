package de.autoapp.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class OperatorCount(val operator: String?, val sites: Long)

data class TileIndex(val tileLat: Long, val tileLon: Long)

@Dao
interface ChargeSiteDao {

    @Query("SELECT * FROM chargeSite WHERE lat BETWEEN :south AND :north AND lon BETWEEN :west AND :east")
    suspend fun sitesInBox(south: Double, north: Double, west: Double, east: Double): List<ChargeSiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSites(sites: List<ChargeSiteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markTilesFetched(tiles: List<TileCoverageEntity>)

    @Insert
    suspend fun markCorridorsFetched(corridors: List<CorridorCoverageEntity>)

    /** One fetch, one transaction: sites and their coverage land together or not at all. */
    @Transaction
    suspend fun recordFetch(
        sites: List<ChargeSiteEntity>,
        tiles: List<TileCoverageEntity>,
        corridors: List<CorridorCoverageEntity>,
    ) {
        upsertSites(sites)
        markTilesFetched(tiles)
        markCorridorsFetched(corridors)
    }

    // Fresh tiles within a tile range. Listed rather than counted: which
    // tiles a query needs depends on its shape, not just on its box.
    @Query(
        "SELECT tileLat, tileLon FROM tileCoverage WHERE sourceId = :sourceId " +
            "AND networkKey = :networkKey " +
            "AND tileLat BETWEEN :minTileLat AND :maxTileLat " +
            "AND tileLon BETWEEN :minTileLon AND :maxTileLon " +
            "AND fetchedAtMillis > :notOlderThanMillis",
    )
    suspend fun freshTilesIn(
        sourceId: String,
        networkKey: String,
        minTileLat: Long,
        maxTileLat: Long,
        minTileLon: Long,
        maxTileLon: Long,
        notOlderThanMillis: Long,
    ): List<TileIndex>

    // Fresh corridors whose box overlaps the given one.
    @Query(
        "SELECT * FROM corridorCoverage WHERE sourceId = :sourceId " +
            "AND networkKey = :networkKey " +
            "AND south <= :north AND north >= :south AND west <= :east AND east >= :west " +
            "AND fetchedAtMillis > :notOlderThanMillis",
    )
    suspend fun freshCorridorsIn(
        sourceId: String,
        networkKey: String,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        notOlderThanMillis: Long,
    ): List<CorridorCoverageEntity>

    @Query("DELETE FROM tileCoverage WHERE sourceId = :sourceId")
    suspend fun clearTileCoverage(sourceId: String)

    @Query("DELETE FROM corridorCoverage WHERE sourceId = :sourceId")
    suspend fun clearCorridorCoverage(sourceId: String)

    /** Discards coverage, not the sites themselves: the store stays readable. */
    @Transaction
    suspend fun clearCoverage(sourceId: String) {
        clearTileCoverage(sourceId)
        clearCorridorCoverage(sourceId)
    }

    @Query("DELETE FROM tileCoverage WHERE fetchedAtMillis <= :olderThanMillis")
    suspend fun pruneStaleTiles(olderThanMillis: Long)

    @Query("DELETE FROM corridorCoverage WHERE fetchedAtMillis <= :olderThanMillis")
    suspend fun pruneStaleCorridors(olderThanMillis: Long)

    @Transaction
    suspend fun pruneStaleCoverage(olderThanMillis: Long) {
        pruneStaleTiles(olderThanMillis)
        pruneStaleCorridors(olderThanMillis)
    }

    @Query("DELETE FROM tileCoverage WHERE networkKey NOT IN (:keys)")
    suspend fun pruneTilesNotIn(keys: List<String>)

    @Query("DELETE FROM corridorCoverage WHERE networkKey NOT IN (:keys)")
    suspend fun pruneCorridorsNotIn(keys: List<String>)

    @Transaction
    suspend fun pruneCoverageNotIn(keys: List<String>) {
        pruneTilesNotIn(keys)
        pruneCorridorsNotIn(keys)
    }

    @Query("DELETE FROM chargeSite WHERE fetchedAtMillis <= :olderThanMillis")
    suspend fun pruneStaleSites(olderThanMillis: Long)

    @Query("DELETE FROM chargeSite")
    suspend fun clearAll()

    // The networks picker wants its list before the first live fetch: what
    // the store already knows is good enough to seed it.
    @Query("SELECT operator, COUNT(*) AS sites FROM chargeSite WHERE operator IS NOT NULL GROUP BY operator")
    suspend fun operatorCounts(): List<OperatorCount>
}
