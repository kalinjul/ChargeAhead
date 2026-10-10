package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Checks the mapping against canned responses of the shape the contract module defines. */
class BackendRouteSiteSourceTest {

    private val from = LatLon(48.9, 11.4)
    private val to = LatLon(49.1, 11.6)

    private fun sourceRespondingWith(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: (HttpRequestData) -> Unit = {},
    ): BackendRouteSiteSource {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return BackendRouteSiteSource(createHttpClient(engine, TestBackend))
    }

    private suspend fun BackendRouteSiteSource.query(networkKeys: Set<String> = emptySet()) =
        routeWithSites(from, to, bufferKm = 3.0, networkKeys = networkKeys, minPowerKw = 50.0)

    private fun response(points: String = """[{"lat": 48.9, "lon": 11.4}, {"lat": 49.1, "lon": 11.6}]""") =
        """
        {
          "route": {
            "points": $points,
            "distanceKm": 94.2,
            "durationMinutes": 63.0,
            "provider": "osm",
            "attribution": "OpenStreetMap",
            "segments": [
              {"fromKm": 0.0, "distanceKm": 94.2, "durationMinutes": 63.0, "ascentM": 120.0, "descentM": 210.0}
            ]
          },
          "sites": [
            {
              "site": {
                "id": "ocm:1",
                "name": "Autohof Nord",
                "position": {"lat": 48.95, "lon": 11.45},
                "network": "enbw",
                "connectors": [{"type": "ccs2", "maxPowerKw": 150.0, "count": 4}],
                "sources": ["ocm"]
              },
              "kmFromStart": 12.5,
              "distanceFromRouteKm": 0.4
            },
            {
              "site": {
                "id": "ocm:2",
                "name": "Rasthof Süd",
                "position": {"lat": 49.05, "lon": 11.55},
                "connectors": [{"type": "ccs2", "maxPowerKw": 300.0, "count": 8}],
                "sources": ["ocm"]
              },
              "kmFromStart": 71.0,
              "distanceFromRouteKm": 2.1
            }
          ],
          "sitesAttribution": "Open Charge Map"
        }
        """.trimIndent()

    @Test
    fun mapsTheRouteAndTheSitesAlongIt() = runBlocking {
        val found = assertNotNull(sourceRespondingWith(response()).query())

        assertEquals(94.2, found.route.distanceKm)
        assertEquals(63.0, found.route.durationMinutes)
        assertEquals(120.0, found.route.segments.single().ascentM)
        assertEquals(listOf("ocm:1", "ocm:2"), found.sites.map { it.site.id })
        val first = found.sites.first()
        assertEquals(12.5, first.kmFromStart)
        assertEquals(0.4, first.distanceFromRouteKm)
        assertEquals("enbw", first.site.networkKey)
        assertEquals(ConnectorType.CCS2, first.site.connectors.single().type)
        assertEquals(150.0, first.site.connectors.single().maxPowerKw)
    }

    @Test
    fun theFiltersTravelInTheBody() = runBlocking {
        var sent = ""
        var url = ""
        val source = sourceRespondingWith("", status = HttpStatusCode.NoContent) {
            sent = (it.body as TextContent).text
            url = it.url.toString()
        }

        source.query(networkKeys = setOf("ionity"))

        assertTrue(url.endsWith("/v1/route/charge-sites"), url)
        assertTrue(""""lat":48.9""" in sent, sent)
        assertTrue(""""bufferKm":3.0""" in sent, sent)
        assertTrue(""""minPowerKw":50.0""" in sent, sent)
        assertTrue(""""networks":[{"key":"ionity"}]""" in sent, sent)
    }

    /** No road connection is an answer, not a failure. */
    @Test
    fun noRoadConnectionIsNull() = runBlocking {
        assertNull(sourceRespondingWith("", status = HttpStatusCode.NoContent).query())
    }

    /** A single point is not a route. */
    @Test
    fun aSinglePointIsNoRoute() = runBlocking {
        assertNull(sourceRespondingWith(response(points = """[{"lat": 48.9, "lon": 11.4}]""")).query())
    }

    /** 502 and 503 must not look like 204. */
    @Test
    fun aServerFailurePropagates() {
        for (status in listOf(HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable)) {
            val mock = MockEngine { respondError(status) }
            val source = BackendRouteSiteSource(createHttpClient(mock, TestBackend))

            assertFailsWith<Exception>(status.toString()) { runBlocking { source.query() } }
        }
    }
}
