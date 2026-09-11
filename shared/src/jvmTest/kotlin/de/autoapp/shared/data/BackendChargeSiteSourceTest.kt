package de.autoapp.shared.data

import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.SectorArea
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks the mapping against canned responses of the shape the contract
 * module defines. That the deployed backend actually answers this way is a
 * separate question — see [BackendChargeSiteLiveContractTest].
 */
class BackendChargeSiteSourceTest {

    private val area = SectorArea.circle(LatLon(48.9331, 11.4779), radiusKm = 150.0)

    private fun sourceRespondingWith(
        body: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ): BackendChargeSiteSource {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return BackendChargeSiteSource(
            createHttpClient(engine),
            baseUrl = "https://example.invalid/",
            token = "test-token",
        )
    }

    private fun bodyOf(request: HttpRequestData): String =
        (request.body as TextContent).text

    @Test
    fun mapsAFullyPopulatedSite() = runBlocking {
        val source = sourceRespondingWith(
            """
            {
              "sites": [{
                "id": "ocm:12345",
                "name": "Autohof Nord",
                "position": {"lat": 48.95, "lon": 11.5},
                "operator": "EnBW",
                "operatorId": 42,
                "connectors": [
                  {"type": "ccs2", "maxPowerKw": 150.0, "count": 4},
                  {"type": "type2", "maxPowerKw": 22.0}
                ],
                "address": {"street": "Am Hof 1", "postalCode": "85049", "town": "Ingolstadt"},
                "sources": ["ocm"]
              }],
              "attribution": "Open Charge Map"
            }
            """.trimIndent(),
        )

        val sites = source.query(area, emptyList())

        assertEquals(1, sites.size)
        val site = sites.single()
        assertEquals("ocm:12345", site.id)
        assertEquals("Autohof Nord", site.name)
        assertEquals("EnBW", site.operator)
        assertEquals(42L, site.operatorId)
        assertEquals(LatLon(48.95, 11.5), site.position)
        assertEquals("Ingolstadt", site.address?.town)
        assertEquals(setOf("ocm"), site.sources)
        assertEquals(ConnectorType.CCS2, site.connectors[0].type)
        assertEquals(4, site.connectors[0].count)
        // Absent means unknown, and must not become 1.
        assertNull(site.connectors[1].count)
    }

    @Test
    fun anEmptyResultIsNotAnError() = runBlocking {
        val sites = sourceRespondingWith("""{"sites": [], "attribution": "x"}""")
            .query(area, emptyList())

        assertTrue(sites.isEmpty())
    }

    /** The corridor goes as one polyline; splitting it into circles is the server's job now. */
    @Test
    fun aCorridorTravelsAsAPolyline() = runBlocking {
        var sent = ""
        val source = sourceRespondingWith("""{"sites": [], "attribution": "x"}""") { sent = bodyOf(it) }

        source.query(
            PolylineArea(listOf(LatLon(48.9, 11.4), LatLon(49.1, 11.6)), bufferKm = 5.0),
            emptyList(),
        )

        assertTrue(""""type":"polyline"""" in sent, sent)
        assertTrue(""""bufferKm":5.0""" in sent, sent)
    }

    @Test
    fun networkFiltersTravelWithTheRequest() = runBlocking {
        var sent = ""
        val source = sourceRespondingWith("""{"sites": [], "attribution": "x"}""") { sent = bodyOf(it) }

        source.query(area, listOf(Network("enbw", "EnBW", setOf(60L), setOf("enbw"))))

        assertTrue(""""key":"enbw"""" in sent, sent)
        assertTrue("60" in sent, sent)
    }

    @Test
    fun theTokenGoesInTheAuthorizationHeader() = runBlocking {
        var header: String? = null
        val source = sourceRespondingWith("""{"sites": [], "attribution": "x"}""") {
            header = it.headers[HttpHeaders.Authorization]
        }

        source.query(area, emptyList())

        assertEquals("Bearer test-token", header)
    }

    /** A failing backend must not look like "no charging site ahead". */
    @Test
    fun aServerErrorPropagates() {
        val engine = MockEngine { respondError(HttpStatusCode.BadGateway) }
        val source = BackendChargeSiteSource(
            createHttpClient(engine),
            baseUrl = "https://example.invalid",
            token = "test-token",
        )

        assertFailsWith<Exception> { runBlocking { source.query(area, emptyList()) } }
    }
}
