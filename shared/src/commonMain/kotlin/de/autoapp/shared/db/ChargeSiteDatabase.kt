package de.autoapp.shared.db

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction

// Charging-station local store, and the question of which area was fetched
// when. Two tables, because there are two questions: WHAT do we know
// (chargeSite) and FOR WHAT do we know that we know it completely
// (tileCoverage). Without the second, there'd be no way to tell whether an
// area has no charging station or whether nobody has ever checked there.

// Spatial queries always run over a rectangle — hence the (lat, lon) index.
@Entity(tableName = "chargeSite", indices = [Index("lat", "lon")])
data class ChargeSiteEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val name: String,
    val operator: String?,
    val operatorId: Long? = null,
    val lat: Double,
    val lon: Double,
    // Connectors as a "type:kW:count" list. A dedicated table would be
    // cleaner, but connectors are never queried on their own: they're always
    // read and written together with their site. A join for that would be
    // effort without payoff. "count" may be empty — the sources often don't
    // state it.
    val connectors: String,
    // Postal address, if the source knows one. Individually nullable: the
    // sources fill in varying amounts of it.
    val street: String?,
    val postalCode: String?,
    val town: String?,
    val fetchedAtMillis: Long,
)

@Entity(tableName = "tileCoverage", primaryKeys = ["sourceId", "networkKey", "tileLat", "tileLon"])
data class TileCoverageEntity(
    val sourceId: String,
    val networkKey: String,
    // Tile indices on a 0.1° grid: floor(degree * 10).
    val tileLat: Long,
    val tileLon: Long,
    val fetchedAtMillis: Long,
)

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

// exportSchema = false for the same reason verifyMigrations was off under
// SQLDelight: a schema snapshot per version costs more than it buys. The
// tables are a regenerable cache, so a schema bump just drops and refetches
// — see fallbackToDestructiveMigration in DatabaseFactory. No hand-written
// Migration objects.
@Database(entities = [ChargeSiteEntity::class, TileCoverageEntity::class], version = 2, exportSchema = false)
@ConstructedBy(ChargeSiteDatabaseConstructor::class)
abstract class ChargeSiteDatabase : RoomDatabase() {
    abstract fun chargeSites(): ChargeSiteDao
}

// The actual is generated by Room's KSP — the documented KMP pattern.
@Suppress("KotlinNoActualForExpect")
expect object ChargeSiteDatabaseConstructor : RoomDatabaseConstructor<ChargeSiteDatabase> {
    override fun initialize(): ChargeSiteDatabase
}
