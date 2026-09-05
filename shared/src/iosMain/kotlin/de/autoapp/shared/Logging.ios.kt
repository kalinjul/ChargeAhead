package de.autoapp.shared

import platform.Foundation.NSLog

// Only compiled on macOS — unverified on this Linux machine.
actual fun logWarning(message: String, cause: Throwable?) {
    NSLog("[%@] %@", LOG_TAG, message + (cause?.let { ": $it" } ?: ""))
}
