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
import org.julakali.chargeahead.shared.settings.SettingsKeys.DC_PEAK
import org.julakali.chargeahead.shared.settings.SettingsKeys.GARAGE
import org.julakali.chargeahead.shared.settings.SettingsKeys.MANUAL_SOC
import org.julakali.chargeahead.shared.settings.SettingsKeys.NAME

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

    override suspend fun removeVehicle(displayName: String) {
        dataStore.edit { preferences ->
            val remaining = preferences.garage().filterNot { it.displayName == displayName }
            if (preferences.selectedVehicle()?.displayName == displayName) {
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
            val updated = if (current.any { it.displayName == profile.displayName }) {
                current.map { if (it.displayName == profile.displayName) profile else it }
            } else {
                current + profile
            }
            writeGarage(updated)
        }
        // The selected vehicle stays on the legacy keys.
        putString(NAME, profile?.displayName)
        putString(BATTERY_KWH, profile?.usableBatteryKwh?.toString())
        putString(CONSUMPTION, profile?.consumptionKwhPer100Km?.toString())
        putString(CONNECTORS, profile?.acceptedConnectors?.joinToString(",") { it.name })
        putString(DC_PEAK, profile?.dcPeakPowerKw?.toString())
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

        return VehicleProfile(
            displayName = getStringOrNull(NAME).orEmpty(),
            usableBatteryKwh = battery,
            consumptionKwhPer100Km = consumption,
            // Unknown connector names are skipped rather than thrown on.
            acceptedConnectors = getStringOrNull(CONNECTORS)
                ?.split(",")
                ?.mapNotNull { name -> ConnectorType.entries.firstOrNull { it.name == name.trim() } }
                ?.toSet()
                .orEmpty(),
            dcPeakPowerKw = getStringOrNull(DC_PEAK)?.toDoubleOrNull(),
        )
    }

    @Serializable
    private data class StoredVehicle(
        val name: String,
        val batteryKwh: Double,
        val consumption: Double,
        val connectors: List<String> = emptyList(),
        val dcPeakKw: Double? = null,
    ) {
        /** Broken numbers cost the entry, not the garage. */
        fun toProfileOrNull(): VehicleProfile? {
            if (batteryKwh <= 0.0 || consumption <= 0.0) return null
            return VehicleProfile(
                displayName = name,
                usableBatteryKwh = batteryKwh,
                consumptionKwhPer100Km = consumption,
                acceptedConnectors = connectors
                    .mapNotNull { stored -> ConnectorType.entries.firstOrNull { it.name == stored } }
                    .toSet(),
                dcPeakPowerKw = dcPeakKw,
            )
        }
    }
}
