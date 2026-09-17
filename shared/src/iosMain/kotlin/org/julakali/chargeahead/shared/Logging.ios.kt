package org.julakali.chargeahead.shared

import platform.Foundation.NSLog

actual fun logWarning(message: String, cause: Throwable?) {
    NSLog("[%@] %@", LOG_TAG, message + (cause?.let { ": $it" } ?: ""))
}
