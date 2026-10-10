package org.julakali.chargeahead.android.car

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import org.julakali.chargeahead.shared.domain.DEFAULT_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.VehicleRepository

/** Stands in for the Room-backed garage: the bundled SQLite driver has no host library under Robolectric. */
class InMemoryVehicleRepository : VehicleRepository {
    private val garage = MutableStateFlow(emptyList<VehicleProfile>())
    private val selectedId = MutableStateFlow<String?>(null)

    override val vehicles: Flow<List<VehicleProfile>> = garage
    override val vehicle: Flow<VehicleProfile?> = combine(garage, selectedId) { cars, id -> cars.firstOrNull { it.id == id } }
    override val manualSocPercent = MutableStateFlow<Double?>(null)
    override val arrivalSocPercent = MutableStateFlow(DEFAULT_ARRIVAL_SOC_PERCENT)

    override suspend fun setVehicle(profile: VehicleProfile?) {
        if (profile != null) {
            garage.update { cars ->
                if (cars.any { it.id == profile.id }) cars.map { if (it.id == profile.id) profile else it } else cars + profile
            }
        }
        selectedId.value = profile?.id
    }

    override suspend fun updateVehicles(transform: (VehicleProfile) -> VehicleProfile) = garage.update { it.map(transform) }

    override suspend fun removeVehicle(id: String) {
        val remaining = garage.value.filterNot { it.id == id }
        if (selectedId.value == id) selectedId.value = remaining.firstOrNull()?.id
        garage.value = remaining
    }

    override suspend fun setManualSocPercent(socPercent: Double?) {
        manualSocPercent.value = socPercent?.coerceIn(0.0, 100.0)
    }

    override suspend fun setArrivalSocPercent(socPercent: Double) {
        arrivalSocPercent.value = socPercent
    }
}
