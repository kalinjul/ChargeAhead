package org.julakali.chargeahead.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** OkHttp on Android and JVM, Darwin on iOS. */
internal expect fun defaultHttpEngine(): HttpClientEngine

/** The shared HTTP client. Timeouts are short for mobile conditions. */
fun createHttpClient(engine: HttpClientEngine = defaultHttpEngine()): HttpClient =
    HttpClient(engine) {
        // Non-2xx should throw.
        expectSuccess = true

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
