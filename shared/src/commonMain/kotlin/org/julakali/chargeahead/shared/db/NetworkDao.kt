package org.julakali.chargeahead.shared.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "network")
data class NetworkEntity(
    @PrimaryKey val key: String,
    val name: String,
    // Position on the backend's current list; null once the network dropped off it.
    val rank: Int?,
)

@Dao
interface NetworkDao {

    @Query("SELECT * FROM network")
    fun observeAll(): Flow<List<NetworkEntity>>

    /** Networks missing from [listed] stay, unranked, so a selection keeps its name. */
    @Transaction
    suspend fun replaceListed(listed: List<NetworkEntity>) {
        unrankAll()
        upsert(listed)
    }

    @Query("UPDATE network SET rank = NULL")
    suspend fun unrankAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(networks: List<NetworkEntity>)
}
