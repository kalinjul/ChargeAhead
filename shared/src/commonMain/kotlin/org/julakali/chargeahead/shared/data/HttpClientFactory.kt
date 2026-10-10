package org.julakali.chargeahead.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import org.julakali.chargeahead.shared.BackendConfig
import org.julakali.chargeahead.shared.currentLanguageTag
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** OkHttp on Android and JVM, Darwin on iOS. */
internal expect fun defaultHttpEngine(): HttpClientEngine

/**
 * The one client the backend sources share: it knows where the backend is and
 * how to get in, so a source only names its path. Timeouts are short for
 * mobile conditions; a server error gets two more tries.
 */
fun createHttpClient(engine: HttpClientEngine = defaultHttpEngine(), backend: BackendConfig): HttpClient =
    HttpClient(engine) {
        // Non-2xx should throw.
        expectSuccess = true

        install(DefaultRequest) {
            // A trailing slash, or a relative path would replace the last segment.
            url(backend.baseUrl.trimEnd('/') + "/")
            bearerAuth(backend.token)
            // Evaluated per request, so a language switch reaches the next call; place names come back in it.
            header(HttpHeaders.AcceptLanguage, currentLanguageTag())
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                },
            )
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
    }
