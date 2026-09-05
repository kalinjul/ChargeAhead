package de.autoapp.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Ktor has no cross-platform engine — each target brings its own
 * (OkHttp on Android and JVM, Darwin on iOS).
 */
internal expect fun defaultHttpEngine(): HttpClientEngine

/**
 * The HTTP client shared by all charging-site sources.
 *
 * Timeouts are tuned for mobile conditions and deliberately short: if a
 * request stalls in a dead zone, a fast failure is better than a list stuck
 * "loading" for minutes — the last known stops stay on screen regardless
 * (see [de.autoapp.shared.ChargeStopsState]).
 */
fun createHttpClient(engine: HttpClientEngine = defaultHttpEngine()): HttpClient =
    HttpClient(engine) {
        // Non-2xx should throw, so a 403 from a missing key doesn't slip
        // through as an empty list that looks like "no charge site ahead."
        expectSuccess = true

        install(ContentNegotiation) {
            json(
                Json {
                    // OCM returns dozens of fields per site; only five are needed.
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
