package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.Network
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendNetworkListSourceTest {

    @Test
    fun mapsTheNetworksInTheirOrder() = runBlocking {
        var path: String? = null
        var authorization: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            authorization = request.headers[HttpHeaders.Authorization]
            respond(
                content = """
                    {"networks":[
                      {"key":"enbw","name":"EnBW","fastSites":2032,"maxPowerKw":400.0,
                       "operatorIds":[86],"nameKeywords":["enbw"]},
                      {"key":"kaufland","name":"Kaufland","fastSites":407,"maxPowerKw":365.0,
                       "nameKeywords":["kaufland"]}
                    ],"generatedAt":"2026-09-19T12:00:00Z"}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val source = BackendNetworkListSource(createHttpClient(engine), "https://example.invalid/", "test-token")

        val networks = source.networks()

        assertEquals("/v1/networks", path)
        assertEquals("Bearer test-token", authorization)
        assertEquals(listOf(Network("enbw", "EnBW"), Network("kaufland", "Kaufland")), networks)
    }
}
