package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkCatalog
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.SectorArea
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests the mapping of the charging station register against canned responses. */
class BnetzaSourceTest {

    private val area = SectorArea.circle(LatLon(48.95, 11.45), radiusKm = 50.0)

    private fun sourceRespondingWith(
        vararg bodies: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ): BnetzaSource {
        var call = 0
        val engine = MockEngine { request ->
            onRequest(request)
            val body = bodies.getOrElse(call) { bodies.last() }
            call++
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return BnetzaSource(createHttpClient(engine))
    }

    private val HttpRequestData.form: Parameters
        get() = (body as FormDataContent).formData

    private fun feature(attributes: String) =
        """{"features":[{"attributes":{$attributes}}],"exceededTransferLimit":false}"""

    @Test
    fun mapsAChargingFacility() = runBlocking {
        val source = sourceRespondingWith(
            feature(
                """"Ladeeinrichtungs_ID":1141226,"Betreiber":"Autostrom plus GmbH",
                   "Standortbezeichnung":"Rastanlage Sophienberg West","Ort":"Bayreuth",
                   "Breitengrad":48.89344,"Längengrad":11.59843,
                   "Steckertypen1":"DC Fahrzeugkupplung Typ Combo 2 (CCS)","Nennleistung_Stecker1":"400"""",
            ),
        )

        val site = source.query(area).single()

        assertEquals("bnetza:1141226", site.id)
        assertEquals("Rastanlage Sophienberg West", site.name)
        assertEquals("Autostrom plus GmbH", site.operator)
        assertEquals(48.89344, site.position.lat)
        assertEquals(11.59843, site.position.lon)
        assertEquals(ConnectorType.CCS2, site.connectors.single().type)
        assertEquals(400.0, site.connectors.single().maxPowerKw)
        assertEquals(1, site.connectors.single().count)
    }

    @Test
    fun multipleConnectorsInOneField_areCounted() = runBlocking {
        // "AC Typ 2 Steckdose; AC Typ 2 Steckdose" with "22; 22" is two units.
        val source = sourceRespondingWith(
            feature(
                """"Ladeeinrichtungs_ID":1,"Breitengrad":48.9,"Längengrad":11.4,
                   "Steckertypen1":"AC Typ 2 Steckdose; AC Typ 2 Steckdose",
                   "Nennleistung_Stecker1":"22; 22"""",
            ),
        )

        val connector = source.query(area).single().connectors.single()

        assertEquals(ConnectorType.TYPE2, connector.type)
        assertEquals(22.0, connector.maxPowerKw)
        assertEquals(2, connector.count)
    }

    @Test
    fun differentTypesInOneField_staySeparate() = runBlocking {
        val source = sourceRespondingWith(
            feature(
                """"Ladeeinrichtungs_ID":1,"Breitengrad":48.9,"Längengrad":11.4,
                   "Steckertypen1":"DC Fahrzeugkupplung Typ Combo 2 (CCS); DC CHAdeMO",
                   "Nennleistung_Stecker1":"175; 175"""",
            ),
        )

        val connectors = source.query(area).single().connectors

        assertEquals(2, connectors.size)
        assertEquals(setOf(ConnectorType.CCS2, ConnectorType.CHADEMO), connectors.map { it.type }.toSet())
        assertTrue(connectors.all { it.maxPowerKw == 175.0 && it.count == 1 })
    }

    @Test
    fun multipleConnectorSlots_areMerged() = runBlocking {
        val source = sourceRespondingWith(
            feature(
                """"Ladeeinrichtungs_ID":1,"Breitengrad":48.9,"Längengrad":11.4,
                   "Steckertypen1":"AC Typ 2 Steckdose","Nennleistung_Stecker1":"22",
                   "Steckertypen2":"AC Typ 2 Steckdose","Nennleistung_Stecker2":"22",
                   "Steckertypen3":"AC Schuko","Nennleistung_Stecker3":"3.7"""",
            ),
        )

        val connectors = source.query(area).single().connectors

        assertEquals(2, connectors.size)
        assertEquals(2, connectors.first { it.type == ConnectorType.TYPE2 }.count)
        assertEquals(1, connectors.first { it.type == ConnectorType.SCHUKO }.count)
    }

    @Test
    fun allSixConnectorLabelsOfTheRegister() = runBlocking {
        // The service's distinct connector values.
        val expected = mapOf(
            "AC Typ 2 Steckdose" to ConnectorType.TYPE2,
            "AC Typ 2 Fahrzeugkupplung" to ConnectorType.TYPE2,
            "DC Fahrzeugkupplung Typ Combo 2 (CCS)" to ConnectorType.CCS2,
            "DC CHAdeMO" to ConnectorType.CHADEMO,
            "AC Schuko" to ConnectorType.SCHUKO,
            "AC CEE 3-polig" to ConnectorType.UNKNOWN,
        )

        expected.forEach { (label, type) ->
            val source = sourceRespondingWith(
                feature(
                    """"Ladeeinrichtungs_ID":1,"Breitengrad":48.9,"Längengrad":11.4,
                       "Steckertypen1":"$label","Nennleistung_Stecker1":"22"""",
                ),
            )
            assertEquals(type, source.query(area).single().connectors.single().type, label)
        }
    }

    @Test
    fun anErrorWithHttp200_isDetected() {
        // ArcGIS puts errors in the response body.
        val source = sourceRespondingWith(
            """{"error":{"code":499,"message":"Token Required"}}""",
        )

        runBlocking {
            val error = assertFailsWith<IllegalStateException> { source.query(area) }
            assertTrue(error.message!!.contains("Token Required"))
        }
    }

    @Test
    fun whenTheTransferLimitIsExceeded_itPaginates() = runBlocking {
        val firstPage =
            """{"features":[{"attributes":{"Ladeeinrichtungs_ID":1,"Breitengrad":48.9,"Längengrad":11.4}}],
               "exceededTransferLimit":true}"""
        val secondPage =
            """{"features":[{"attributes":{"Ladeeinrichtungs_ID":2,"Breitengrad":48.9,"Längengrad":11.4}}],
               "exceededTransferLimit":false}"""

        val offsets = mutableListOf<String?>()
        val source = sourceRespondingWith(firstPage, secondPage) {
            offsets += it.form["resultOffset"]
        }

        val sites = source.query(area)

        assertEquals(listOf("bnetza:1", "bnetza:2"), sites.map { it.id })
        assertEquals(listOf<String?>("0", "1"), offsets)
    }

    @Test
    fun withoutCoordinates_theRowIsDropped() = runBlocking {
        val source = sourceRespondingWith(
            """{"features":[
                 {"attributes":{"Ladeeinrichtungs_ID":1,"Standortbezeichnung":"Ohne Lage"}},
                 {"attributes":{"Ladeeinrichtungs_ID":2,"Breitengrad":48.9,"Längengrad":11.4}}
               ],"exceededTransferLimit":false}""",
        )

        assertEquals(listOf("bnetza:2"), source.query(area).map { it.id })
    }

    @Test
    fun withoutSiteName_theCityIsUsed() = runBlocking {
        val source = sourceRespondingWith(
            feature(""""Ladeeinrichtungs_ID":1,"Ort":"Greding","Breitengrad":48.9,"Längengrad":11.4"""),
        )

        assertEquals("Greding", source.query(area).single().name)
    }

    @Test
    fun withoutOperator_theFieldStaysEmpty() = runBlocking {
        val source = sourceRespondingWith(
            feature(""""Ladeeinrichtungs_ID":1,"Breitengrad":48.9,"Längengrad":11.4"""),
        )

        assertNull(source.query(area).single().operator)
    }

    @Test
    fun builds_where_with_keyword_likes() = runBlocking {
        var where: String? = null
        val source = sourceRespondingWith("""{"features":[],"exceededTransferLimit":false}""") {
            where = it.form["where"]
        }
        val sel = NetworkCatalog.selection(setOf("enbw", "ionity"))
        source.query(SectorArea.circle(LatLon(48.1, 11.5), 5.0), networks = sel)
        assertEquals(
            "Status='In Betrieb' AND (UPPER(Betreiber) LIKE '%ENBW%' OR UPPER(Betreiber) LIKE '%IONITY%')",
            where,
        )
    }

    @Test
    fun base_clause_when_unfiltered() = runBlocking {
        var where: String? = null
        val source = sourceRespondingWith("""{"features":[],"exceededTransferLimit":false}""") {
            where = it.form["where"]
        }
        source.query(SectorArea.circle(LatLon(48.1, 11.5), 5.0))
        assertEquals("Status='In Betrieb'", where)
    }

    @Test
    fun queriesOnlyOperationalFacilitiesWithinTheBoundingBox() = runBlocking {
        var seen: HttpRequestData? = null
        val source = sourceRespondingWith("""{"features":[],"exceededTransferLimit":false}""") {
            seen = it
        }

        source.query(area)

        val parameter = requireNotNull(seen).form
        assertEquals("Status='In Betrieb'", parameter["where"])
        assertEquals("esriGeometryEnvelope", parameter["geometryType"])
        assertEquals("4326", parameter["inSR"])
        assertEquals("false", parameter["returnGeometry"])
        assertTrue(parameter["geometry"]!!.contains("\"xmin\""))
    }

    /** A long polyline is sent as a POST body. */
    @Test
    fun aLongRouteTravelsInTheBody() = runBlocking {
        var seen: HttpRequestData? = null
        val source = sourceRespondingWith("""{"features":[],"exceededTransferLimit":false}""") {
            seen = it
        }
        val route = PolylineArea((0..500).map { LatLon(48.0 + it * 0.002, 11.0 + it * 0.001) }, bufferKm = 2.0)

        source.query(route)

        val request = requireNotNull(seen)
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.toString().length < 200, request.url.toString())
        assertEquals("esriGeometryPolyline", request.form["geometryType"])
        assertEquals("2000.0", request.form["distance"])
        assertTrue(request.form["geometry"]!!.startsWith("""{"paths":[[[11.0,48.0],"""))
    }
}
