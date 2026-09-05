package de.autoapp.shared.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

// Only compiled on macOS (see shared/build.gradle.kts) — unverified on this
// Linux machine.
internal actual fun defaultHttpEngine(): HttpClientEngine = Darwin.create()
