package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.ConnectorType
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks the mapping of the OCM response onto the domain model against canned
 * responses. What this does *not* check: whether the real interface actually
 * responds this way. See the warning on [OpenChargeMapSource].
 *
 * `runBlocking` instead of `runTest`, so the tests need no extra dependency
 * (kotlinx-coroutines-test); that's why they live in the jvmTest source set
 * rather than commonTest.
 */
class OpenChargeMapSourceTest {

    private val area = SectorArea.circle(LatLon(48.9331, 11.4779), radiusKm = 150.0)

    private fun sourceRespondingWith(
        body: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ): OpenChargeMapSource {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return OpenChargeMapSource(createHttpClient(engine), apiKey = "test-api-key")
    }

    @Test
    fun mapsAFullyPopulatedSite() = runBlocking {
        val source = sourceRespondingWith(
            """
            [{
              "ID": 12345,
              "AddressInfo": { "Title": "EnBW Jura-West", "Latitude": 48.9331, "Longitude": 11.4779 },
              "OperatorInfo": { "Title": "EnBW" },
              "Connections": [
                { "ConnectionTypeID": 33, "PowerKW": 300.0, "Quantity": 6 },
                { "ConnectionTypeID": 25, "PowerKW": 22.0, "Quantity": 2 }
              ]
            }]
            """.trimIndent(),
        )

        val site = source.query(area).single()

        assertEquals("ocm:12345", site.id)
        assertEquals("EnBW Jura-West", site.name)
        assertEquals("EnBW", site.operator)
        assertEquals(48.9331, site.position.lat)
        assertEquals(11.4779, site.position.lon)
        assertEquals(
            listOf(ConnectorType.CCS2, ConnectorType.TYPE2),
            site.connectors.map { it.type },
        )
        assertEquals(6, site.connectors.first().count)
        assertEquals(300.0, site.connectors.first().maxPowerKw)
    }

    @Test
    fun skipsSitesWithoutCoordinates() {
        // A site without a position can neither be ranked nor navigated to —
        // it must not fill the list with a row that has no distance.
        val source = sourceRespondingWith(
            """
            [
              { "ID": 1, "AddressInfo": { "Title": "Ohne Lage" } },
              { "ID": 2, "AddressInfo": { "Title": "Mit Lage", "Latitude": 48.5, "Longitude": 11.5 } }
            ]
            """.trimIndent(),
        )

        runBlocking {
            assertEquals(listOf("ocm:2"), source.query(area).map { it.id })
        }
    }

    @Test
    fun usesTheLocationAsName_whenTheTitleIsMissing() = runBlocking {
        val source = sourceRespondingWith(
            """[{ "ID": 7, "AddressInfo": { "Town": "Greding", "Latitude": 49.0, "Longitude": 11.3 } }]""",
        )

        assertEquals("Greding", source.query(area).single().name)
    }

    @Test
    fun withoutOperator_theFieldStaysEmpty() = runBlocking {
        val source = sourceRespondingWith(
            """[{ "ID": 7, "AddressInfo": { "Title": "X", "Latitude": 49.0, "Longitude": 11.3 } }]""",
        )

        assertNull(source.query(area).single().operator)
    }

    @Test
    fun discardsConnectorsWithoutPowerRating() = runBlocking {
        // Showing "0 kW" in the car would be worse than omitting the connector entirely.
        val source = sourceRespondingWith(
            """
            [{
              "ID": 7,
              "AddressInfo": { "Title": "X", "Latitude": 49.0, "Longitude": 11.3 },
              "Connections": [
                { "ConnectionTypeID": 33 },
                { "ConnectionTypeID": 33, "PowerKW": 0.0 },
                { "ConnectionTypeID": 33, "PowerKW": 150.0 }
              ]
            }]
            """.trimIndent(),
        )

        assertEquals(1, source.query(area).single().connectors.size)
    }

    @Test
    fun unknownConnectorTypeNumber_becomesVisiblyUnknown() = runBlocking {
        val source = sourceRespondingWith(
            """
            [{
              "ID": 7,
              "AddressInfo": { "Title": "X", "Latitude": 49.0, "Longitude": 11.3 },
              "Connections": [{ "ConnectionTypeID": 999, "PowerKW": 11.0 }]
            }]
            """.trimIndent(),
        )

        assertEquals(ConnectorType.UNKNOWN, source.query(area).single().connectors.single().type)
    }

    @Test
    fun missingQuantity_staysUnknown() = runBlocking {
        // Don't default to 1: OCM is missing this count for a good half of
        // connectors, and a fabricated number doesn't belong in the car.
        val source = sourceRespondingWith(
            """
            [{
              "ID": 7,
              "AddressInfo": { "Title": "X", "Latitude": 49.0, "Longitude": 11.3 },
              "Connections": [{ "ConnectionTypeID": 2, "PowerKW": 50.0 }]
            }]
            """.trimIndent(),
        )

        assertNull(source.query(area).single().connectors.single().count)
    }

    @Test
    fun quantityZero_countsAsUnknown() = runBlocking {
        // 22 of 307 connectors in the sample carried a 0.
        val source = sourceRespondingWith(
            """
            [{
              "ID": 7,
              "AddressInfo": { "Title": "X", "Latitude": 49.0, "Longitude": 11.3 },
              "Connections": [{ "ConnectionTypeID": 2, "PowerKW": 50.0, "Quantity": 0 }]
            }]
            """.trimIndent(),
        )

        assertNull(source.query(area).single().connectors.single().count)
    }

    @Test
    fun chaoJi_countsAsChademo() = runBlocking {
        val source = sourceRespondingWith(
            """
            [{
              "ID": 7,
              "AddressInfo": { "Title": "X", "Latitude": 49.0, "Longitude": 11.3 },
              "Connections": [{ "ConnectionTypeID": 1044, "PowerKW": 500.0 }]
            }]
            """.trimIndent(),
        )

        assertEquals(ConnectorType.CHADEMO, source.query(area).single().connectors.single().type)
    }

    @Test
    fun placeholderOperators_areDiscarded() = runBlocking {
        // OCM lists three placeholders as regular operators. In the sample,
        // 38 of 123 sites carried one; unfiltered, the car would show
        // "Ladepark X · (Business Owner at Location)".
        val placeholders = listOf(
            "(Business Owner at Location)",
            "(Private Residence/Individual)",
            "(Unknown Operator)",
        )

        placeholders.forEach { title ->
            val source = sourceRespondingWith(
                """
                [{
                  "ID": 7,
                  "AddressInfo": { "Title": "X", "Latitude": 49.0, "Longitude": 11.3 },
                  "OperatorInfo": { "Title": "$title" }
                }]
                """.trimIndent(),
            )

            assertNull(source.query(area).single().operator, "Placeholder '$title' got through")
        }
    }

    @Test
    fun realOperatorNamesWithParentheses_arePreserved() = runBlocking {
        // Of 997 operators in the reference data, exactly the three
        // placeholders start with "(". These here are real.
        val real = listOf("EnBW (D)", "Shell Recharge Solutions (DE)", "Tesla (including non-tesla)")

        real.forEach { title ->
            val source = sourceRespondingWith(
                """
                [{
                  "ID": 7,
                  "AddressInfo": { "Title": "X", "Latitude": 49.0, "Longitude": 11.3 },
                  "OperatorInfo": { "Title": "$title" }
                }]
                """.trimIndent(),
            )

            assertEquals(title, source.query(area).single().operator)
        }
    }

    @Test
    fun unknownFieldsInTheResponse_doNotInterfere() = runBlocking {
        // OCM returns dozens of fields per site and keeps adding new ones.
        val source = sourceRespondingWith(
            """
            [{
              "ID": 7, "UUID": "abc", "DataProviderID": 1, "UsageCost": "kostenlos",
              "StatusType": { "IsOperational": true },
              "AddressInfo": { "Title": "X", "Latitude": 49.0, "Longitude": 11.3, "Postcode": "91171" },
              "Connections": [{ "ConnectionTypeID": 2, "PowerKW": 50.0, "LevelID": 3 }]
            }]
            """.trimIndent(),
        )

        assertEquals("ocm:7", source.query(area).single().id)
    }

    @Test
    fun sendsTheKeyAndSearchAreaAlong() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val source = sourceRespondingWith("[]") { capturedRequest = it }

        source.query(area)

        val request = requireNotNull(capturedRequest)
        assertEquals("test-api-key", request.headers[OpenChargeMapSource.API_KEY_HEADER])
        assertEquals("test-api-key", request.url.parameters["key"])
        assertEquals("500", request.url.parameters["maxresults"])
    }

    @Test
    fun queriesRadiallyNotRectangularly() = runBlocking {
        // A rectangular query gets an unsorted response from OCM that's
        // truncated at maxresults — cutting out exactly the nearest charging
        // stations. See the reasoning in OpenChargeMapSource.query.
        var capturedRequest: HttpRequestData? = null
        val source = sourceRespondingWith("[]") { capturedRequest = it }

        source.query(area)

        val parameters = requireNotNull(capturedRequest).url.parameters
        assertEquals("48.9331", parameters["latitude"])
        assertEquals("11.4779", parameters["longitude"])
        assertEquals("150.0", parameters["distance"])
        assertEquals("KM", parameters["distanceunit"])
        assertNull(parameters["boundingbox"], "Rectangular query would be unsorted")
    }

    @Test
    fun anInterfaceErrorIsPropagated() {
        // Without a key, OCM responds with 403. That must not be read as "no
        // charging station ahead" — it has to surface as an error further up.
        val engine = MockEngine { respondError(HttpStatusCode.Forbidden) }
        val source = OpenChargeMapSource(createHttpClient(engine), apiKey = "falsch")

        runBlocking {
            assertFailsWith<Exception> { source.query(area) }
        }
    }

    @Test
    fun emptyResponse_resultsInEmptyList() = runBlocking {
        assertTrue(sourceRespondingWith("[]").query(area).isEmpty())
    }

    @Test
    fun carries_operator_id() = runBlocking {
        val json = """[{"ID":1,"OperatorID":86,"OperatorInfo":{"Title":"EnBW"},
            "AddressInfo":{"Latitude":48.1,"Longitude":11.5},"Connections":[]}]"""
        val source = sourceRespondingWith(json)
        val sites = source.query(area)
        assertEquals(86L, sites.single().operatorId)
    }
}
