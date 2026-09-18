package org.julakali.chargeahead.shared.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.julakali.chargeahead.shared.domain.ChargePointState
import org.julakali.chargeahead.shared.domain.ConnectorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackendChargePointStatusSourceTest {

    private val requests = mutableListOf<List<String>>()

    private fun sourceRespondingWith(body: (List<String>) -> String) = BackendChargePointStatusSource(
        createHttpClient(
            MockEngine { request ->
                assertTrue(request.url.toString().endsWith("/v1/charge-point-status"))
                val ids = Json.parseToJsonElement((request.body as TextContent).text)
                    .jsonObject.getValue("siteIds").jsonArray.map { it.jsonPrimitive.content }
                requests += ids
                respond(
                    content = body(ids),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ),
        baseUrl = "https://example.invalid/",
        token = "test-token",
    )

    @Test
    fun mapsStatusesAndTreatsUnknownValuesAsUnknown() = runBlocking {
        val source = sourceRespondingWith {
            """
            {"sites": [{"siteId": "m:1", "chargePoints": [
              {"id": "DE*A*1", "status": "available", "maxPowerKw": 150.0, "connectors": ["ccs2"]},
              {"id": "DE*A*2", "status": "out_of_order"},
              {"id": "DE*A*3", "status": "charging_on_the_moon"}
            ]}]}
            """.trimIndent()
        }

        val status = source.status(listOf("m:1", "m:2"))

        val points = status.getValue("m:1")
        assertEquals(
            listOf(ChargePointState.AVAILABLE, ChargePointState.OUT_OF_ORDER, ChargePointState.UNKNOWN),
            points.map { it.state },
        )
        assertEquals(listOf(ConnectorType.CCS2), points[0].connectors)
        assertEquals(setOf("m:1"), status.keys)
    }

    @Test
    fun largeRequestsAreSplit() = runBlocking {
        val source = sourceRespondingWith { ids ->
            ids.joinToString(",", prefix = """{"sites": [""", postfix = "]}") {
                """{"siteId": "$it", "chargePoints": []}"""
            }
        }
        val ids = (1..1_200).map { "m:$it" }

        val status = source.status(ids)

        assertEquals(listOf(500, 500, 200), requests.map { it.size })
        assertEquals(ids.toSet(), status.keys)
    }
}
