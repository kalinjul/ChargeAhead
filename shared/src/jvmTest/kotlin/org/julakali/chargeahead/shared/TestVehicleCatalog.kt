package org.julakali.chargeahead.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.RoadLoad
import org.julakali.chargeahead.shared.domain.VehicleCatalogRepository
import org.julakali.chargeahead.shared.domain.VehiclePreset

val testPresets = listOf(
    VehiclePreset(
        id = "48d30005-4e77-446c-bfcf-3152d1884a65",
        name = "Fiat 500e 42 kWh",
        usableBatteryKwh = 37.3,
        consumptionKwhPer100Km = 13.8,
        dcPeakPowerKw = 85.0,
        connectors = setOf(ConnectorType.CCS2, ConnectorType.TYPE2),
        roadLoad = RoadLoad(114.064, 0.47199, 0.0232062, 2000.0, 0.94, 1.0, 0.65, wltpKwhPer100Km = 13.8),
    ),
    VehiclePreset(
        id = "408b5c7a-4982-49d6-ab22-f8336ef4b49e",
        name = "MG4 Urban 54 kWh",
        usableBatteryKwh = 52.8,
        consumptionKwhPer100Km = 15.3,
        dcPeakPowerKw = 87.0,
        connectors = setOf(ConnectorType.CCS2, ConnectorType.TYPE2),
        roadLoad = RoadLoad(132.344, 0.547632, 0.0269253, 2000.0, 0.94, 1.0, 0.65),
    ),
)

class FakeVehicleCatalog(presets: List<VehiclePreset> = testPresets) : VehicleCatalogRepository {
    override val presets: Flow<List<VehiclePreset>> = MutableStateFlow(presets)
    override suspend fun refresh() = Unit
}
