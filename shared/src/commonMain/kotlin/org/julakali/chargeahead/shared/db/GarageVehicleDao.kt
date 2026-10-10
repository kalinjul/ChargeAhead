package org.julakali.chargeahead.shared.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "garageVehicle")
data class GarageVehicleEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double,
    // ConnectorType names.
    val connectors: Set<String>,
    val dcPeakPowerKw: Double?,
    val modelId: String?,
    val customized: Boolean,
    val ownConsumption: Boolean,
    val ownName: Boolean,
)

@Dao
interface GarageVehicleDao {

    // An upsert keeps the rowid, so a car stays where it was added.
    @Query("SELECT * FROM garageVehicle ORDER BY rowid")
    fun observeAll(): Flow<List<GarageVehicleEntity>>

    @Query("SELECT * FROM garageVehicle ORDER BY rowid")
    suspend fun all(): List<GarageVehicleEntity>

    @Upsert
    suspend fun upsert(vehicles: List<GarageVehicleEntity>)

    @Transaction
    suspend fun updateAll(transform: (GarageVehicleEntity) -> GarageVehicleEntity) {
        upsert(all().map(transform))
    }

    @Query("DELETE FROM garageVehicle WHERE id = :id")
    suspend fun delete(id: String)
}
