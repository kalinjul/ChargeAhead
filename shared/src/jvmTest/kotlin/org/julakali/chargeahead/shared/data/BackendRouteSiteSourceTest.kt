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

    private val oneSegment =
        """"segments": [{"fromKm": 0.0, "distanceKm": 94.2, "durationMinutes": 63.0, "ascentM": 120.0, "descentM": 210.0}]"""

    /** [extra] is the route's optional fields, comma-separated; empty for none. */
    private fun response(
        points: String = """[{"lat": 48.9, "lon": 11.4}, {"lat": 49.1, "lon": 11.6}]""",
        extra: String = oneSegment,
    ) =
        """
        {
          "route": {
            "points": $points,
            "distanceKm": 94.2,
            "durationMinutes": 63.0,
            "provider": "osm",
            "attribution": "OpenStreetMap"${if (extra.isEmpty()) "" else ", $extra"}
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

    /** The backend builds the segments; the client only carries them across. */
    @Test
    fun theSpeedProfileIsCarriedAcross() = runBlocking {
        val source = sourceRespondingWith(
            response(
                extra = """
                    "segments": [
                      {"fromKm": 0.0, "distanceKm": 8.2, "durationMinutes": 12.0, "ascentM": 41.0, "descentM": 3.5},
                      {"fromKm": 8.2, "distanceKm": 86.0, "durationMinutes": 51.0, "ascentM": 120.0, "descentM": 210.0}
                    ]
                """.trimIndent(),
            ),
        )

        val segments = assertNotNull(source.query()).route.segments

        assertEquals(2, segments.size)
        assertEquals(0.0, segments[0].fromKm)
        assertEquals(8.2, segments[0].distanceKm)
        assertEquals(12.0, segments[0].durationMinutes)
        assertEquals(41.0, segments[0].ascentM)
        assertEquals(3.5, segments[0].descentM)
        assertEquals(8.2, segments[1].fromKm)
        assertEquals(210.0, segments[1].descentM)
        assertTrue(segments[1].averageSpeedKmh > segments[0].averageSpeedKmh)
    }

    @Test
    fun aSegmentWithoutElevationIsRejected() {
        val source = sourceRespondingWith(
            response(extra = """"segments": [{"fromKm": 0.0, "distanceKm": 94.2, "durationMinutes": 63.0}]"""),
        )

        assertFailsWith<IllegalArgumentException> { runBlocking { source.query() } }
    }

    @Test
    fun aRouteWithoutAProfileStillMaps() = runBlocking {
        val route = assertNotNull(sourceRespondingWith(response(extra = "")).query()).route

        assertTrue(route.segments.isEmpty())
        assertEquals(94.2, route.distanceKm)
    }

    @Test
    fun theEncodedLineWinsOverThePoints() = runBlocking {
        val source = sourceRespondingWith(response(extra = """"encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@""""))

        val points = assertNotNull(source.query()).route.points

        assertEquals(3, points.size)
        assertEquals(38.5, points.first().lat, 1e-9)
        assertEquals(-126.453, points.last().lon, 1e-9)
    }

    /** A line that decodes to less than a route must not throw away the usable points. */
    @Test
    fun anUnusableEncodedLineFallsBackToThePoints() = runBlocking {
        val source = sourceRespondingWith(response(extra = """"encodedPolyline": "_p~iF""""))

        assertEquals(listOf(LatLon(48.9, 11.4), LatLon(49.1, 11.6)), assertNotNull(source.query()).route.points)
    }

    @Test
    fun theTokenGoesInTheAuthorizationHeader() = runBlocking {
        var header: String? = null
        val source = sourceRespondingWith("", status = HttpStatusCode.NoContent) {
            header = it.headers[HttpHeaders.Authorization]
        }

        source.query()

        assertEquals("Bearer test-token", header)
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
        assertTrue("48.9" !in url, url)
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
