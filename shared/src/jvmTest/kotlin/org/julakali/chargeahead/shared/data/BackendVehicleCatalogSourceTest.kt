package org.julakali.chargeahead.shared.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.RoadLoad
import org.julakali.chargeahead.shared.domain.VehiclePreset
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendVehicleCatalogSourceTest {

    @Test
    fun `maps the models in their order`() = runBlocking {
        var path: String? = null
        var authorization: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            authorization = request.headers[HttpHeaders.Authorization]
            respond(
                content = """
                    {"vehicles":[
                      {"id":"48d30005-4e77-446c-bfcf-3152d1884a65","name":"Fiat 500e 42 kWh",
                       "usableBatteryKwh":37.3,"consumptionKwhPer100Km":13.8,"dcPeakPowerKw":85.0,
                       "connectors":["ccs2","type2"],
                       "roadLoad":{"f0":120.0,"f1":0.5,"f2":0.03,"massKg":1400.0,"drivetrainEfficiency":0.88,
                                   "auxiliaryPowerKw":0.6,"recuperationShare":0.6,"source":"generic","wltpKwhPer100Km":13.8}},
                      {"id":"7d0e7c3a-0000-4000-8000-000000000001","name":"Stadtflitzer",
                       "usableBatteryKwh":20.0,"consumptionKwhPer100Km":12.0,"dcPeakPowerKw":0.0,
                       "connectors":["type2"],
                       "roadLoad":{"f0":100.0,"f1":0.4,"f2":0.025,"massKg":1000.0,"drivetrainEfficiency":0.88,
                                   "auxiliaryPowerKw":0.5,"recuperationShare":0.6,"source":"generic"}}
                    ]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val source = BackendVehicleCatalogSource(createHttpClient(engine, TestBackend))

        val presets = source.presets()

        assertEquals("/v1/vehicles", path)
        assertEquals("Bearer test-token", authorization)
        assertEquals(
            listOf(
                VehiclePreset(
                    "48d30005-4e77-446c-bfcf-3152d1884a65", "Fiat 500e 42 kWh", 37.3, 13.8, 85.0,
                    setOf(ConnectorType.CCS2, ConnectorType.TYPE2),
                    RoadLoad(120.0, 0.5, 0.03, 1400.0, 0.88, 0.6, 0.6, wltpKwhPer100Km = 13.8),
                ),
                VehiclePreset(
                    "7d0e7c3a-0000-4000-8000-000000000001", "Stadtflitzer", 20.0, 12.0, 0.0, setOf(ConnectorType.TYPE2),
                    RoadLoad(100.0, 0.4, 0.025, 1000.0, 0.88, 0.5, 0.6),
                ),
            ),
            presets,
        )
    }
}
