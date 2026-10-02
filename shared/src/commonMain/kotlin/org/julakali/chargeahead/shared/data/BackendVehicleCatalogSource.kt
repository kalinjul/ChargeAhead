package org.julakali.chargeahead.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.julakali.chargeahead.api.VehiclesResponse
import org.julakali.chargeahead.shared.domain.RoadLoad
import org.julakali.chargeahead.shared.domain.VehicleCatalogSource
import org.julakali.chargeahead.shared.domain.VehiclePreset

class BackendVehicleCatalogSource(
    private val httpClient: HttpClient,
) : VehicleCatalogSource {

    override suspend fun presets(): List<VehiclePreset> {
        val response: VehiclesResponse = httpClient.get(PATH).body()
        return response.vehicles.map { model ->
            VehiclePreset(
                id = model.id,
                name = model.name,
                usableBatteryKwh = model.usableBatteryKwh,
                consumptionKwhPer100Km = model.consumptionKwhPer100Km,
                dcPeakPowerKw = model.dcPeakPowerKw,
                connectors = model.connectors.mapTo(LinkedHashSet()) { it.toDomain() },
                roadLoad = with(model.roadLoad) {
                    RoadLoad(
                        f0, f1, f2, massKg, drivetrainEfficiency, auxiliaryPowerKw, recuperationShare,
                        wltpKwhPer100Km = requireNotNull(wltpKwhPer100Km) { "Road load of ${model.id} without its WLTP consumption" },
                    )
                },
            )
        }
    }

    private companion object {
        const val PATH = "v1/vehicles"
    }
}
