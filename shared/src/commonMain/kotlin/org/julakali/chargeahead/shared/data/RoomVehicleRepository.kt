package org.julakali.chargeahead.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.db.GarageVehicleEntity
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.DEFAULT_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.MAX_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.settings.SettingsKeys.ARRIVAL_SOC
import org.julakali.chargeahead.shared.settings.SettingsKeys.MANUAL_SOC
import org.julakali.chargeahead.shared.settings.SettingsKeys.SELECTED_VEHICLE
import org.julakali.chargeahead.shared.settings.getStringOrNull
import org.julakali.chargeahead.shared.settings.putString
import org.julakali.chargeahead.shared.settings.read

/** The garage lives in the database; the settings file holds which car is selected, and the charge levels. */
class RoomVehicleRepository(
    private val dataStore: DataStore<Preferences>,
    database: ChargeSiteDatabase,
) : VehicleRepository {

    private val dao = database.garageVehicles()

    override val vehicles: Flow<List<VehicleProfile>> = dao.observeAll().map { entities ->
        entities.map { it.toProfile() }
    }

    override val vehicle: Flow<VehicleProfile?> =
        combine(dataStore.read { it.getStringOrNull(SELECTED_VEHICLE) }, vehicles) { selectedId, garage ->
            garage.firstOrNull { it.id == selectedId }
        }.distinctUntilChanged()

    override val manualSocPercent: Flow<Double?> = dataStore.read {
        it.getStringOrNull(MANUAL_SOC)?.toDoubleOrNull()?.coerceIn(0.0, 100.0)
    }

    override val arrivalSocPercent: Flow<Double> = dataStore.read {
        it.getStringOrNull(ARRIVAL_SOC)?.toDoubleOrNull()?.coerceIn(0.0, MAX_ARRIVAL_SOC_PERCENT)
            ?: DEFAULT_ARRIVAL_SOC_PERCENT
    }

    override suspend fun setVehicle(profile: VehicleProfile?) {
        if (profile != null) dao.upsert(listOf(profile.toEntity()))
        dataStore.edit { it.putString(SELECTED_VEHICLE, profile?.id) }
    }

    override suspend fun updateVehicles(transform: (VehicleProfile) -> VehicleProfile) {
        dao.updateAll { transform(it.toProfile()).toEntity() }
    }

    override suspend fun removeVehicle(id: String) {
        val next = dao.all().firstOrNull { it.id != id }
        // The selection moves first, so it never points at a car that is gone.
        dataStore.edit { if (it.getStringOrNull(SELECTED_VEHICLE) == id) it.putString(SELECTED_VEHICLE, next?.id) }
        dao.delete(id)
    }

    override suspend fun setManualSocPercent(socPercent: Double?) {
        dataStore.edit { it.putString(MANUAL_SOC, socPercent?.coerceIn(0.0, 100.0)?.toString()) }
    }

    override suspend fun setArrivalSocPercent(socPercent: Double) {
        dataStore.edit { it.putString(ARRIVAL_SOC, socPercent.coerceIn(0.0, MAX_ARRIVAL_SOC_PERCENT).toString()) }
    }

    private fun VehicleProfile.toEntity() = GarageVehicleEntity(
        id = id,
        displayName = displayName,
        usableBatteryKwh = usableBatteryKwh,
        consumptionKwhPer100Km = consumptionKwhPer100Km,
        connectors = acceptedConnectors.mapTo(LinkedHashSet()) { it.name },
        dcPeakPowerKw = dcPeakPowerKw,
        modelId = modelId,
        customized = customized,
        ownConsumption = ownConsumption,
        ownName = ownName,
    )

    private fun GarageVehicleEntity.toProfile() = VehicleProfile(
        id = id,
        displayName = displayName,
        usableBatteryKwh = usableBatteryKwh,
        consumptionKwhPer100Km = consumptionKwhPer100Km,
        // Unknown connector names are skipped rather than thrown on.
        acceptedConnectors = connectors.mapNotNullTo(LinkedHashSet()) { stored ->
            ConnectorType.entries.firstOrNull { it.name == stored }
        },
        dcPeakPowerKw = dcPeakPowerKw,
        modelId = modelId,
        customized = customized,
        ownConsumption = ownConsumption,
        ownName = ownName,
    )
}
