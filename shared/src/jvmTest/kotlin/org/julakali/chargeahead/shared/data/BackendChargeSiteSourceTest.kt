package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.PriceComponent
import org.julakali.chargeahead.shared.domain.PriceKind
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.Weekday
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

/** Checks the mapping against canned responses of the shape the contract module defines. */
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
            createHttpClient(engine, TestBackend),
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
                "network": "enbw",
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

        val sites = source.query(area, emptySet())

        assertEquals(1, sites.size)
        val site = sites.single()
        assertEquals("ocm:12345", site.id)
        assertEquals("Autohof Nord", site.name)
        assertEquals("EnBW", site.operator)
        assertEquals(42L, site.operatorId)
        assertEquals("enbw", site.networkKey)
        assertEquals(LatLon(48.95, 11.5), site.position)
        assertEquals("Ingolstadt", site.address?.town)
        assertEquals(setOf("ocm"), site.sources)
        assertEquals(ConnectorType.CCS2, site.connectors[0].type)
        assertEquals(4, site.connectors[0].count)
        // Absent means unknown, and must not become 1.
        assertNull(site.connectors[1].count)
    }

    @Test
    fun mapsAdHocTariffs() = runBlocking {
        val source = sourceRespondingWith(
            """
            {
              "sites": [{
                "id": "mobilithek:1",
                "name": "A",
                "position": {"lat": 48.9, "lon": 11.4},
                "adHocTariffs": [{
                  "currency": "EUR",
                  "components": [
                    {"type": "per_kwh", "price": 0.59, "startTime": "08:00:00+01:00", "endTime": "20:00:00+01:00", "days": ["monday", "friday"]},
                    {"type": "per_minute", "price": 0.1, "fromMinute": 240},
                    {"type": "session", "price": 1.0},
                    {"type": "parking", "price": 2.0}
                  ],
                  "connectors": ["ccs2"]
                }]
              }],
              "attribution": ""
            }
            """.trimIndent(),
        )

        val tariff = source.query(area, emptySet()).single().adHocTariffs.single()

        assertEquals("EUR", tariff.currency)
        assertEquals(listOf(ConnectorType.CCS2), tariff.connectors)
        assertEquals(
            listOf(
                PriceComponent(
                    PriceKind.PER_KWH, 0.59,
                    startTime = "08:00:00+01:00", endTime = "20:00:00+01:00",
                    days = setOf(Weekday.MONDAY, Weekday.FRIDAY),
                ),
                PriceComponent(PriceKind.PER_MINUTE, 0.1, fromMinute = 240),
                PriceComponent(PriceKind.SESSION, 1.0),
                // A type the app does not know yet.
                PriceComponent(PriceKind.OTHER, 2.0),
            ),
            tariff.components,
        )
    }

    @Test
    fun aSiteWithoutPublishedPricesHasNoTariffs() = runBlocking {
        val site = sourceRespondingWith(
            """{"sites": [{"id": "ocm:2", "name": "B", "position": {"lat": 48.9, "lon": 11.5}}], "attribution": ""}""",
        ).query(area, emptySet()).single()

        assertTrue(site.adHocTariffs.isEmpty())
    }

    @Test
    fun onlyASiteWithLiveStatusCarriesAStatusId() = runBlocking {
        val source = sourceRespondingWith(
            """
            {
              "sites": [
                {"id": "mobilithek:1", "name": "A", "position": {"lat": 48.9, "lon": 11.4}, "hasLiveStatus": true},
                {"id": "ocm:2", "name": "B", "position": {"lat": 48.9, "lon": 11.5}}
              ],
              "attribution": ""
            }
            """.trimIndent(),
        )

        val sites = source.query(area, emptySet())

        assertEquals("mobilithek:1", sites[0].liveStatusId)
        assertNull(sites[1].liveStatusId)
    }

    @Test
    fun anEmptyResultIsNotAnError() = runBlocking {
        val sites = sourceRespondingWith("""{"sites": [], "attribution": "x"}""")
            .query(area, emptySet())

        assertTrue(sites.isEmpty())
    }

    /** The corridor goes as one polyline. */
    @Test
    fun aCorridorTravelsAsAPolyline() = runBlocking {
        var sent = ""
        val source = sourceRespondingWith("""{"sites": [], "attribution": "x"}""") { sent = bodyOf(it) }

        source.query(
            PolylineArea(listOf(LatLon(48.9, 11.4), LatLon(49.1, 11.6)), bufferKm = 5.0),
            emptySet(),
        )

        assertTrue(""""type":"polyline"""" in sent, sent)
        assertTrue(""""bufferKm":5.0""" in sent, sent)
    }

    @Test
    fun networkFiltersTravelWithTheRequest() = runBlocking {
        var sent = ""
        val source = sourceRespondingWith("""{"sites": [], "attribution": "x"}""") { sent = bodyOf(it) }

        source.query(area, setOf("enbw"))

        assertTrue(""""networks":[{"key":"enbw"}]""" in sent, sent)
    }

    @Test
    fun theTokenGoesInTheAuthorizationHeader() = runBlocking {
        var header: String? = null
        val source = sourceRespondingWith("""{"sites": [], "attribution": "x"}""") {
            header = it.headers[HttpHeaders.Authorization]
        }

        source.query(area, emptySet())

        assertEquals("Bearer test-token", header)
    }

    /** A failing backend must not look like "no charging site ahead". */
    @Test
    fun aServerErrorPropagates() {
        val engine = MockEngine { respondError(HttpStatusCode.BadGateway) }
        val source = BackendChargeSiteSource(
            createHttpClient(engine, TestBackend),
        )

        assertFailsWith<Exception> { runBlocking { source.query(area, emptySet()) } }
    }
}
