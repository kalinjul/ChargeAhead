package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackendGeocoderTest {

    private fun geocoderRespondingWith(
        body: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ): BackendGeocoder {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return BackendGeocoder(
            createHttpClient(engine),
            baseUrl = "https://example.invalid/",
            token = "test-token",
        )
    }

    @Test
    fun mapsAPlace() = runBlocking {
        val geocoder = geocoderRespondingWith(
            """
            {
              "places": [{
                "name": "Münchner Freiheit",
                "description": "Münchner Freiheit, Schwabing, München",
                "position": {"lat": 48.162, "lon": 11.586},
                "address": {"street": "Münchner Freiheit", "postalCode": "80802", "town": "München"},
                "distanceKm": 3.4
              }],
              "provider": "osm",
              "attribution": "OpenStreetMap"
            }
            """.trimIndent(),
        )

        val places = geocoder.search("Münchner Freiheit", near = LatLon(48.137, 11.575), limit = 5)

        val place = places.single()
        assertEquals("Münchner Freiheit", place.name)
        assertEquals("Münchner Freiheit, Schwabing, München", place.description)
        assertEquals(LatLon(48.162, 11.586), place.position)
        assertEquals("80802", place.address?.postalCode)
    }

    /** No upstream call and no request for something nobody typed. */
    @Test
    fun anEmptyQueryAsksNobody() = runBlocking {
        var asked = false
        val geocoder = geocoderRespondingWith("""{"places": [], "provider": "osm", "attribution": "x"}""") {
            asked = true
        }

        assertTrue(geocoder.search("   ").isEmpty())
        assertTrue(!asked, "A blank input must not reach the backend")
    }

    @Test
    fun theOriginAndTheLimitTravelAsQueryParameters() = runBlocking {
        var url = ""
        val geocoder = geocoderRespondingWith("""{"places": [], "provider": "osm", "attribution": "x"}""") {
            url = it.url.toString()
        }

        geocoder.search("Kiel", near = LatLon(48.137, 11.575), limit = 7)

        assertTrue("lat=48.137" in url, url)
        assertTrue("lon=11.575" in url, url)
        assertTrue("limit=7" in url, url)
    }

    @Test
    fun theTokenGoesInTheAuthorizationHeader() = runBlocking {
        var header: String? = null
        val geocoder = geocoderRespondingWith("""{"places": [], "provider": "osm", "attribution": "x"}""") {
            header = it.headers[HttpHeaders.Authorization]
        }

        geocoder.search("Kiel")

        assertEquals("Bearer test-token", header)
    }

    /** A throttled or dead backend must not look like "no such place". */
    @Test
    fun aServerErrorPropagates() {
        val engine = MockEngine { respondError(HttpStatusCode.TooManyRequests) }
        val geocoder = BackendGeocoder(createHttpClient(engine), "https://example.invalid", "test-token")

        assertFailsWith<Exception> { runBlocking { geocoder.search("Kiel") } }
    }
}
