package org.julakali.chargeahead.shared.data

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

class BackendRouteEngineTest {

    private val from = LatLon(48.9, 11.4)
    private val to = LatLon(49.1, 11.6)

    private fun engineRespondingWith(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: (HttpRequestData) -> Unit = {},
    ): BackendRouteEngine {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return BackendRouteEngine(
            createHttpClient(engine),
            baseUrl = "https://example.invalid/",
            token = "test-token",
        )
    }

    /** The backend builds the segments; the client only carries them across. */
    @Test
    fun theSpeedProfileIsCarriedAcross() = runBlocking {
        val engine = engineRespondingWith(
            """
            {
              "points": [{"lat": 48.9, "lon": 11.4}, {"lat": 49.1, "lon": 11.6}],
              "distanceKm": 94.2,
              "durationMinutes": 63.0,
              "provider": "osm",
              "attribution": "OpenStreetMap",
              "segments": [
                {"fromKm": 0.0, "distanceKm": 8.2, "durationMinutes": 12.0},
                {"fromKm": 8.2, "distanceKm": 86.0, "durationMinutes": 51.0}
              ]
            }
            """.trimIndent(),
        )

        val route = assertNotNull(engine.route(from, to))

        assertEquals(2, route.segments.size)
        assertEquals(0.0, route.segments[0].fromKm)
        assertEquals(8.2, route.segments[0].distanceKm)
        assertEquals(12.0, route.segments[0].durationMinutes)
        assertEquals(8.2, route.segments[1].fromKm)
        assertTrue(route.segments[1].averageSpeedKmh > route.segments[0].averageSpeedKmh)
    }

    /** An older server sends no profile at all, and the field must stay optional. */
    @Test
    fun aResponseWithoutAProfileStillMaps() = runBlocking {
        val engine = engineRespondingWith(
            """
            {
              "points": [{"lat": 48.9, "lon": 11.4}, {"lat": 49.1, "lon": 11.6}],
              "distanceKm": 94.2,
              "durationMinutes": 63.0,
              "provider": "osm",
              "attribution": "OpenStreetMap"
            }
            """.trimIndent(),
        )

        val route = assertNotNull(engine.route(from, to))

        assertTrue(route.segments.isEmpty())
        assertEquals(94.2, route.distanceKm)
    }

    @Test
    fun mapsARoute() = runBlocking {
        val engine = engineRespondingWith(
            """
            {
              "points": [
                {"lat": 48.9, "lon": 11.4},
                {"lat": 49.0, "lon": 11.5},
                {"lat": 49.1, "lon": 11.6}
              ],
              "distanceKm": 94.2,
              "durationMinutes": 63.0,
              "provider": "osm",
              "attribution": "OpenStreetMap"
            }
            """.trimIndent(),
        )

        val route = engine.route(from, to)!!

        assertEquals(3, route.points.size)
        assertEquals(LatLon(48.9, 11.4), route.points.first())
        assertEquals(94.2, route.distanceKm)
        assertEquals(63.0, route.durationMinutes)
    }

    /** The coarse `points` are only for apps that predate the encoded line. */
    @Test
    fun theEncodedLineWinsOverThePoints() = runBlocking {
        val engine = engineRespondingWith(
            """
            {
              "points": [{"lat": 48.9, "lon": 11.4}, {"lat": 49.1, "lon": 11.6}],
              "encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
              "distanceKm": 94.2,
              "durationMinutes": 63.0,
              "provider": "osm",
              "attribution": "OpenStreetMap"
            }
            """.trimIndent(),
        )

        val route = assertNotNull(engine.route(from, to))

        assertEquals(3, route.points.size)
        assertEquals(38.5, route.points.first().lat, 1e-9)
        assertEquals(-126.453, route.points.last().lon, 1e-9)
    }

    /** A line that decodes to less than a route must not throw away the usable points. */
    @Test
    fun anUnusableEncodedLineFallsBackToThePoints() = runBlocking {
        val engine = engineRespondingWith(
            """
            {
              "points": [{"lat": 48.9, "lon": 11.4}, {"lat": 49.1, "lon": 11.6}],
              "encodedPolyline": "_p~iF",
              "distanceKm": 94.2,
              "durationMinutes": 63.0,
              "provider": "osm",
              "attribution": "OpenStreetMap"
            }
            """.trimIndent(),
        )

        val route = assertNotNull(engine.route(from, to))

        assertEquals(listOf(LatLon(48.9, 11.4), LatLon(49.1, 11.6)), route.points)
    }

    /** No road connection is an answer, not a failure — and carries no body to read. */
    @Test
    fun noRoadConnectionIsNull() = runBlocking {
        val engine = engineRespondingWith("", status = HttpStatusCode.NoContent)

        assertNull(engine.route(from, to))
    }

    /** The domain type rejects a single point; rejecting it here keeps the crash out. */
    @Test
    fun aSinglePointIsNoRoute() = runBlocking {
        val engine = engineRespondingWith(
            """
            {"points": [{"lat": 48.9, "lon": 11.4}], "distanceKm": 0.0,
             "durationMinutes": 0.0, "provider": "osm", "attribution": "x"}
            """.trimIndent(),
        )

        assertNull(engine.route(from, to))
    }

    /** In the body, not the query: the destination must not reach an access log. */
    @Test
    fun theCoordinatesTravelInTheBody() = runBlocking {
        var sent = ""
        var url = ""
        val engine = engineRespondingWith("", status = HttpStatusCode.NoContent) {
            sent = (it.body as TextContent).text
            url = it.url.toString()
        }

        engine.route(from, to)

        assertTrue(""""lat":48.9""" in sent, sent)
        assertTrue("48.9" !in url, url)
    }

    @Test
    fun theTokenGoesInTheAuthorizationHeader() = runBlocking {
        var header: String? = null
        val engine = engineRespondingWith("", status = HttpStatusCode.NoContent) {
            header = it.headers[HttpHeaders.Authorization]
        }

        engine.route(from, to)

        assertEquals("Bearer test-token", header)
    }

    /** 502 means the upstream is down — worth a retry, so it must not look like 204. */
    @Test
    fun anUpstreamFailurePropagates() {
        val mock = MockEngine { respondError(HttpStatusCode.BadGateway) }
        val engine = BackendRouteEngine(createHttpClient(mock), "https://example.invalid", "test-token")

        assertFailsWith<Exception> { runBlocking { engine.route(from, to) } }
    }
}
