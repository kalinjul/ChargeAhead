package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NominatimGeocoderTest {

    private fun geocoderRespondingWith(
        body: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ) = NominatimGeocoder(
        createHttpClient(
            MockEngine { request ->
                onRequest(request)
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
    )

    private val hauptbahnhof = """
        [{"name":"München Hauptbahnhof",
          "display_name":"München Hauptbahnhof, Klinikviertel, München, Bayern, 80335, Deutschland",
          "lat":"48.1407253","lon":"11.5569426","type":"station"}]
    """.trimIndent()

    @Test
    fun mapsAResult() = runBlocking {
        val place = geocoderRespondingWith(hauptbahnhof).search("München Hauptbahnhof").single()

        assertEquals("München Hauptbahnhof", place.name)
        assertTrue(place.description.endsWith("Deutschland"))
        assertEquals(48.1407253, place.position.lat)
        assertEquals(11.5569426, place.position.lon)
    }

    @Test
    fun coordinatesArriveAsStringsAndAreConverted() = runBlocking {
        // Nominatim returns lat/lon as strings. Left unchecked, this would be a
        // runtime deserialization error instead of one caught at compile time.
        val place = geocoderRespondingWith(hauptbahnhof).search("x").single()

        assertTrue(place.position.lat > 48.0 && place.position.lat < 49.0)
    }

    @Test
    fun withoutAShortName_theFirstPartOfTheDescriptionIsUsed() = runBlocking {
        val body = """[{"display_name":"Greding, Roth, Bayern, Deutschland","lat":"49.05","lon":"11.35"}]"""

        assertEquals("Greding", geocoderRespondingWith(body).search("Greding").single().name)
    }

    @Test
    fun resultsWithoutCoordinates_areDropped() = runBlocking {
        val body = """[{"name":"Ohne Lage"},{"name":"Mit Lage","lat":"49.0","lon":"11.0"}]"""

        assertEquals(listOf("Mit Lage"), geocoderRespondingWith(body).search("x").map { it.name })
    }

    @Test
    fun unusableCoordinates_areDropped() = runBlocking {
        val body = """[{"name":"Kaputt","lat":"keine Zahl","lon":"11.0"}]"""

        assertTrue(geocoderRespondingWith(body).search("x").isEmpty())
    }

    @Test
    fun theSamePlaceTwice_appearsOnlyOnce() = runBlocking {
        // A real response to "Münster" from Kiel: OSM carries the place both as
        // a boundary relation and as a point, and Nominatim returns both. Two
        // identically labeled rows are indistinguishable to the driver — and
        // once crashed the app while rendering.
        val body = """
            [{"name":"Munster","display_name":"Munster, Heidekreis, Niedersachsen, 29633, Deutschland",
              "lat":"52.9895","lon":"10.0885"},
             {"name":"Münster","display_name":"Münster, Nordrhein-Westfalen, Deutschland",
              "lat":"51.9625","lon":"7.6252"},
             {"name":"Munster","display_name":"Munster, Heidekreis, Niedersachsen, 29633, Deutschland",
              "lat":"52.9908","lon":"10.0876"}]
        """.trimIndent()

        val results = geocoderRespondingWith(body).search("Münster")

        assertEquals(2, results.size)
        assertEquals(1, results.count { it.description.startsWith("Munster, Heidekreis") })
        // The first one wins: Nominatim sorts by relevance.
        assertEquals(52.9895, results.first().position.lat)
    }

    @Test
    fun anEmptyInput_doesNotQueryAtAll() = runBlocking {
        var wasQueried = false
        val geocoder = geocoderRespondingWith("[]") { wasQueried = true }

        assertTrue(geocoder.search("   ").isEmpty())
        assertEquals(false, wasQueried, "An empty input must not trigger a request")
    }

    @Test
    fun sendsTheRequiredUserAgentAndTheFormat() = runBlocking {
        var seenRequest: HttpRequestData? = null
        geocoderRespondingWith("[]") { seenRequest = it }.search("München")

        val request = requireNotNull(seenRequest)
        // The usage policy requires an identifiable User-Agent.
        assertEquals(OpenChargeMapSource.USER_AGENT, request.headers[HttpHeaders.UserAgent])
        assertEquals("jsonv2", request.url.parameters["format"])
        assertEquals("München", request.url.parameters["q"])
        assertNull(request.url.parameters["viewbox"])
    }

    @Test
    fun withLocation_theSurroundingAreaIsPreferred() = runBlocking {
        var seenRequest: HttpRequestData? = null
        geocoderRespondingWith("[]") { seenRequest = it }.search("Hauptbahnhof", near = LatLon(48.0, 11.0))

        val viewbox = requireNotNull(seenRequest).url.parameters["viewbox"]
        assertEquals("10.0,47.0,12.0,49.0", viewbox)
        // Without bounded=1, far-away destinations remain findable.
        assertNull(requireNotNull(seenRequest).url.parameters["bounded"])
    }
}
