package org.julakali.chargeahead.shared.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vehicleModel")
data class VehicleModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double,
    val dcPeakPowerKw: Double,
    // ConnectorType names.
    val connectors: Set<String>,
    // Position on the backend's list.
    val position: Int,
)

@Dao
interface VehicleModelDao {

    @Query("SELECT * FROM vehicleModel ORDER BY position")
    fun observeAll(): Flow<List<VehicleModelEntity>>

    @Transaction
    suspend fun replaceAll(models: List<VehicleModelEntity>) {
        deleteAll()
        insert(models)
    }

    @Query("DELETE FROM vehicleModel")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(models: List<VehicleModelEntity>)
}
