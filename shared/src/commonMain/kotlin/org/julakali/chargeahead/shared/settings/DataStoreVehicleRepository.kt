package org.julakali.chargeahead.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.DEFAULT_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.MAX_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.settings.SettingsKeys.ARRIVAL_SOC
import org.julakali.chargeahead.shared.settings.SettingsKeys.BATTERY_KWH
import org.julakali.chargeahead.shared.settings.SettingsKeys.CONNECTORS
import org.julakali.chargeahead.shared.settings.SettingsKeys.CONSUMPTION
import org.julakali.chargeahead.shared.settings.SettingsKeys.CUSTOMIZED
import org.julakali.chargeahead.shared.settings.SettingsKeys.DC_PEAK
import org.julakali.chargeahead.shared.settings.SettingsKeys.GARAGE
import org.julakali.chargeahead.shared.settings.SettingsKeys.ID
import org.julakali.chargeahead.shared.settings.SettingsKeys.MANUAL_SOC
import org.julakali.chargeahead.shared.settings.SettingsKeys.MODEL_ID
import org.julakali.chargeahead.shared.settings.SettingsKeys.NAME
import org.julakali.chargeahead.shared.settings.SettingsKeys.OWN_CONSUMPTION
import org.julakali.chargeahead.shared.settings.SettingsKeys.OWN_NAME

/** A corrupt profile is treated as "no profile". */
class DataStoreVehicleRepository(
    private val dataStore: DataStore<Preferences>,
) : VehicleRepository {

    override val vehicle: Flow<VehicleProfile?> = dataStore.read { it.selectedVehicle() }

    override val vehicles: Flow<List<VehicleProfile>> = dataStore.read { it.garage() }

    override val manualSocPercent: Flow<Double?> = dataStore.read {
        it.getStringOrNull(MANUAL_SOC)?.toDoubleOrNull()?.coerceIn(0.0, 100.0)
    }

    override val arrivalSocPercent: Flow<Double> = dataStore.read {
        it.getStringOrNull(ARRIVAL_SOC)?.toDoubleOrNull()?.coerceIn(0.0, MAX_ARRIVAL_SOC_PERCENT)
            ?: DEFAULT_ARRIVAL_SOC_PERCENT
    }

    override suspend fun setVehicle(profile: VehicleProfile?) {
        dataStore.edit { it.writeVehicle(profile) }
    }

    override suspend fun updateVehicles(transform: (VehicleProfile) -> VehicleProfile) {
        dataStore.edit { preferences ->
            val selected = preferences.selectedVehicle()
            val updated = preferences.garage().map(transform)
            preferences.writeGarage(updated)
            if (selected != null) {
                preferences.writeVehicle(updated.firstOrNull { it.id == selected.id } ?: transform(selected))
            }
        }
    }

    override suspend fun removeVehicle(id: String) {
        dataStore.edit { preferences ->
            val remaining = preferences.garage().filterNot { it.id == id }
            if (preferences.selectedVehicle()?.id == id) {
                preferences.writeVehicle(remaining.firstOrNull())
            }
            preferences.writeGarage(remaining)
        }
    }

    override suspend fun setManualSocPercent(socPercent: Double?) {
        dataStore.edit { it.putString(MANUAL_SOC, socPercent?.coerceIn(0.0, 100.0)?.toString()) }
    }

    override suspend fun setArrivalSocPercent(socPercent: Double) {
        dataStore.edit { it.putString(ARRIVAL_SOC, socPercent.coerceIn(0.0, MAX_ARRIVAL_SOC_PERCENT).toString()) }
    }

    private fun MutablePreferences.writeVehicle(profile: VehicleProfile?) {
        if (profile != null) {
            val current = garage()
            // A known car is updated in its slot; only a new one is appended.
            val updated = if (current.any { it.id == profile.id }) {
                current.map { if (it.id == profile.id) profile else it }
            } else {
                current + profile
            }
            writeGarage(updated)
        }
        // The selected vehicle stays on the legacy keys.
        putString(ID, profile?.id)
        putString(NAME, profile?.displayName)
        putString(BATTERY_KWH, profile?.usableBatteryKwh?.toString())
        putString(CONSUMPTION, profile?.consumptionKwhPer100Km?.toString())
        putString(CONNECTORS, profile?.acceptedConnectors?.joinToString(",") { it.name })
        putString(DC_PEAK, profile?.dcPeakPowerKw?.toString())
        putString(MODEL_ID, profile?.modelId)
        putString(CUSTOMIZED, profile?.customized?.takeIf { it }?.toString())
        putString(OWN_CONSUMPTION, profile?.ownConsumption?.takeIf { it }?.toString())
        putString(OWN_NAME, profile?.ownName?.takeIf { it }?.toString())
    }

    private fun MutablePreferences.writeGarage(vehicles: List<VehicleProfile>) {
        putJson(
            GARAGE,
            vehicles.takeIf { it.isNotEmpty() }?.map {
                StoredVehicle(
                    name = it.displayName,
                    batteryKwh = it.usableBatteryKwh,
                    consumption = it.consumptionKwhPer100Km,
                    connectors = it.acceptedConnectors.map(ConnectorType::name),
                    dcPeakKw = it.dcPeakPowerKw,
                    id = it.id,
                    modelId = it.modelId,
                    customized = it.customized,
                    ownConsumption = it.ownConsumption,
                    ownName = it.ownName,
                )
            },
        )
    }

    /** Adopts a vehicle that exists only in the legacy keys. */
    private fun Preferences.garage(): List<VehicleProfile> =
        getJson<List<StoredVehicle>>(GARAGE).orEmpty()
            .mapNotNull { it.toProfileOrNull() }
            .ifEmpty { listOfNotNull(selectedVehicle()) }

    private fun Preferences.selectedVehicle(): VehicleProfile? {
        val battery = getStringOrNull(BATTERY_KWH)?.toDoubleOrNull() ?: return null
        val consumption = getStringOrNull(CONSUMPTION)?.toDoubleOrNull() ?: return null
        if (battery <= 0.0 || consumption <= 0.0) return null
        val name = getStringOrNull(NAME).orEmpty()
        val modelId = getStringOrNull(MODEL_ID)

        return VehicleProfile(
            id = getStringOrNull(ID) ?: legacyId(modelId, name),
            displayName = name,
            usableBatteryKwh = battery,
            consumptionKwhPer100Km = consumption,
            // Unknown connector names are skipped rather than thrown on.
            acceptedConnectors = getStringOrNull(CONNECTORS)
                ?.split(",")
                ?.mapNotNull { name -> ConnectorType.entries.firstOrNull { it.name == name.trim() } }
                ?.toSet()
                .orEmpty(),
            dcPeakPowerKw = getStringOrNull(DC_PEAK)?.toDoubleOrNull(),
            modelId = modelId,
            customized = getStringOrNull(CUSTOMIZED) == "true",
            ownConsumption = getStringOrNull(OWN_CONSUMPTION) == "true",
            ownName = getStringOrNull(OWN_NAME) == "true",
        )
    }

    @Serializable
    private data class StoredVehicle(
        val name: String,
        val batteryKwh: Double,
        val consumption: Double,
        val connectors: List<String> = emptyList(),
        val dcPeakKw: Double? = null,
        val id: String? = null,
        val modelId: String? = null,
        val customized: Boolean = false,
        val ownConsumption: Boolean = false,
        val ownName: Boolean = false,
    ) {
        /** Broken numbers cost the entry, not the garage. */
        fun toProfileOrNull(): VehicleProfile? {
            if (batteryKwh <= 0.0 || consumption <= 0.0) return null
            return VehicleProfile(
                id = id ?: legacyId(modelId, name),
                displayName = name,
                usableBatteryKwh = batteryKwh,
                consumptionKwhPer100Km = consumption,
                acceptedConnectors = connectors
                    .mapNotNull { stored -> ConnectorType.entries.firstOrNull { it.name == stored } }
                    .toSet(),
                dcPeakPowerKw = dcPeakKw,
                modelId = modelId,
                customized = customized,
                ownConsumption = ownConsumption,
                ownName = ownName,
            )
        }
    }
}

/** Cars stored before they had an id get one that is the same on every read, in the garage and as the selection. */
private fun legacyId(modelId: String?, name: String): String = modelId ?: "name:$name"
