package de.autoapp.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class OperatorCount(val operator: String?, val sites: Long)

@Dao
interface ChargeSiteDao {

    @Query("SELECT * FROM chargeSite WHERE lat BETWEEN :south AND :north AND lon BETWEEN :west AND :east")
    suspend fun sitesInBox(south: Double, north: Double, west: Double, east: Double): List<ChargeSiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSites(sites: List<ChargeSiteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markTilesFetched(tiles: List<TileCoverageEntity>)

    /** One fetch, one transaction: sites and their coverage land together or not at all. */
    @Transaction
    suspend fun recordFetch(sites: List<ChargeSiteEntity>, tiles: List<TileCoverageEntity>) {
        upsertSites(sites)
        markTilesFetched(tiles)
    }

    // Tiles of the area that are fresh enough. The caller compares the count
    // against the number of requested tiles: if one is missing, it refetches.
    @Query(
        "SELECT count(*) FROM tileCoverage WHERE sourceId = :sourceId " +
            "AND networkKey = :networkKey " +
            "AND tileLat BETWEEN :minTileLat AND :maxTileLat " +
            "AND tileLon BETWEEN :minTileLon AND :maxTileLon " +
            "AND fetchedAtMillis > :notOlderThanMillis",
    )
    suspend fun freshTileCount(
        sourceId: String,
        networkKey: String,
        minTileLat: Long,
        maxTileLat: Long,
        minTileLon: Long,
        maxTileLon: Long,
        notOlderThanMillis: Long,
    ): Long

    /** Discards coverage, not the sites themselves: the store stays readable. */
    @Query("DELETE FROM tileCoverage WHERE sourceId = :sourceId")
    suspend fun clearCoverage(sourceId: String)

    @Query("DELETE FROM tileCoverage WHERE fetchedAtMillis <= :olderThanMillis")
    suspend fun pruneStaleCoverage(olderThanMillis: Long)

    @Query("DELETE FROM tileCoverage WHERE networkKey NOT IN (:keys)")
    suspend fun pruneCoverageNotIn(keys: List<String>)

    @Query("DELETE FROM chargeSite WHERE fetchedAtMillis <= :olderThanMillis")
    suspend fun pruneStaleSites(olderThanMillis: Long)

    @Query("DELETE FROM chargeSite")
    suspend fun clearAll()

    // The networks picker wants its list before the first live fetch: what
    // the store already knows is good enough to seed it.
    @Query("SELECT operator, COUNT(*) AS sites FROM chargeSite WHERE operator IS NOT NULL GROUP BY operator")
    suspend fun operatorCounts(): List<OperatorCount>
}
