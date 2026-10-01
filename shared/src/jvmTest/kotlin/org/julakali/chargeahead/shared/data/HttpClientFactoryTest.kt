package org.julakali.chargeahead.shared.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.BackendConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The one client every backend source shares: it knows where the backend is and how to get in. */
class HttpClientFactoryTest {

    private val backend = BackendConfig("https://example.invalid/", "test-token")

    @Test
    fun `relative paths land on the backend with the bearer token`() = runBlocking {
        var url = ""
        var authorization: String? = null
        val engine = MockEngine { request ->
            url = request.url.toString()
            authorization = request.headers[HttpHeaders.Authorization]
            respond("{}", HttpStatusCode.OK)
        }

        createHttpClient(engine, backend).get("v1/networks")

        assertEquals("https://example.invalid/v1/networks", url)
        assertEquals("Bearer test-token", authorization)
    }

    @Test
    fun `a server error is retried, then given up on`() = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls++
            respondError(HttpStatusCode.BadGateway)
        }

        assertFailsWith<Exception> { createHttpClient(engine, backend).get("v1/networks") }

        assertEquals(3, calls, "one try and two retries")
    }

    @Test
    fun `a client error is not retried`() = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls++
            respondError(HttpStatusCode.NotFound)
        }

        assertFailsWith<ClientRequestException> { createHttpClient(engine, backend).get("v1/networks") }

        assertEquals(1, calls)
    }
}
