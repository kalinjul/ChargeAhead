package org.julakali.chargeahead.shared.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** The trip the driver last planned. One row, which the next plan replaces. */
@Entity(tableName = "plannedTrip")
data class PlannedTripEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    // Stored as JSON: the plan is read and written whole, never queried into.
    val plan: String,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}

@Dao
interface PlannedTripDao {

    @Query("SELECT plan FROM plannedTrip WHERE id = :id")
    suspend fun load(id: Int = PlannedTripEntity.SINGLE_ROW_ID): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(trip: PlannedTripEntity)

    @Query("DELETE FROM plannedTrip")
    suspend fun clear()
}
