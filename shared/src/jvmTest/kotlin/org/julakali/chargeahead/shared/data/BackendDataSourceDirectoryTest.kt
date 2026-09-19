package org.julakali.chargeahead.shared.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.domain.Dataset
import org.julakali.chargeahead.shared.domain.DatasetKind
import org.julakali.chargeahead.shared.domain.License
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendDataSourceDirectoryTest {

    @Test
    fun mapsSourcesAndTheirDatasets() = runBlocking {
        var path: String? = null
        var auth: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            auth = request.headers[HttpHeaders.Authorization]
            respond(
                content = """
                {"sources": [{
                  "id": "mobilithek:enbw", "name": "EnBW", "url": "https://mobilithek.info",
                  "datasets": [
                    {"kind": "status", "url": "https://mobilithek.info/offers/1",
                     "license": {"name": "CC BY 4.0", "url": "https://creativecommons.org/licenses/by/4.0/"}},
                    {"kind": "weather"}
                  ]
                }]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val directory = BackendDataSourceDirectory(createHttpClient(engine), "https://example.invalid/", "test-token")

        val source = directory.dataSources().single()

        assertEquals("/v1/data-sources", path)
        assertEquals("Bearer test-token", auth)
        assertEquals("EnBW", source.name)
        assertEquals(
            listOf(
                Dataset(
                    DatasetKind.STATUS,
                    "https://mobilithek.info/offers/1",
                    License("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
                ),
                // An unknown kind from a newer backend is kept, not dropped.
                Dataset(DatasetKind.OTHER, null, null),
            ),
            source.datasets,
        )
    }
}
