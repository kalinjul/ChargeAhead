package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsrmRouteEngineTest {

    private val nuernberg = LatLon(49.4521, 11.0767)
    private val muenchen = LatLon(48.1351, 11.5820)

    private fun engineRespondingWith(
        body: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ) = OsrmRouteEngine(
        createHttpClient(
            MockEngine { request ->
                onRequest(request)
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
    )

    private val validResponse = """
        {"code":"Ok","routes":[{
          "distance":170123.4,"duration":6480.0,
          "geometry":{"type":"LineString","coordinates":[
            [11.0767,49.4521],[11.19,49.19],[11.46,48.93],[11.5820,48.1351]]}
        }]}
    """.trimIndent()

    @Test
    fun mapsARoute() = runBlocking {
        val route = assertNotNull(engineRespondingWith(validResponse).route(nuernberg, muenchen))

        assertEquals(4, route.points.size)
        assertTrue(abs(route.distanceKm - 170.12) < 0.01, "Was ${route.distanceKm}")
        assertTrue(abs(route.durationMinutes - 108.0) < 0.01, "Was ${route.durationMinutes}")
    }

    @Test
    fun geoJsonIsLongitudeBeforeLatitude() = runBlocking {
        // The single most common source of coordinate bugs. Swapped, the
        // route would land in the Indian Ocean instead of Bavaria.
        val route = assertNotNull(engineRespondingWith(validResponse).route(nuernberg, muenchen))

        assertEquals(49.4521, route.points.first().lat)
        assertEquals(11.0767, route.points.first().lon)
        assertEquals(48.1351, route.points.last().lat)
    }

    @Test
    fun withoutARoadConnection_returnsNullNotAnError() = runBlocking {
        // "NoRoute" is a response, not an error — retrying won't help.
        assertNull(engineRespondingWith("""{"code":"NoRoute","routes":[]}""").route(nuernberg, muenchen))
    }

    @Test
    fun anEmptyRouteList_resultsInNull() = runBlocking {
        assertNull(engineRespondingWith("""{"code":"Ok","routes":[]}""").route(nuernberg, muenchen))
    }

    @Test
    fun aSingleWaypoint_resultsInNull() = runBlocking {
        // A route made of a single point cannot be buffered.
        val body = """{"code":"Ok","routes":[{"distance":0,"duration":0,
                       "geometry":{"coordinates":[[11.0,49.0]]}}]}"""

        assertNull(engineRespondingWith(body).route(nuernberg, muenchen))
    }

    @Test
    fun queriesTheSimplifiedGeometry() = runBlocking {
        var seenRequest: HttpRequestData? = null
        engineRespondingWith(validResponse) { seenRequest = it }.route(nuernberg, muenchen)

        val request = requireNotNull(seenRequest)
        assertEquals("simplified", request.url.parameters["overview"])
        assertEquals("geojson", request.url.parameters["geometries"])
        // Longitude before latitude, separated by a semicolon.
        assertTrue(
            request.url.encodedPath.endsWith("11.0767,49.4521;11.582,48.1351"),
            "Path was ${request.url.encodedPath}",
        )
    }

    @Test
    fun unknownFieldsDoNotCauseProblems(): Unit = runBlocking {
        val body = """{"code":"Ok","waypoints":[{"name":"A9"}],"routes":[{
            "distance":1000.0,"duration":60.0,"weight":42.0,"weight_name":"routability",
            "legs":[{"summary":"A9"}],
            "geometry":{"type":"LineString","coordinates":[[11.0,49.0],[11.1,49.1]]}}]}"""

        assertNotNull(engineRespondingWith(body).route(nuernberg, muenchen))
    }
}
