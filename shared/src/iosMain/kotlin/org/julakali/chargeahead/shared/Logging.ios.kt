package org.julakali.chargeahead.shared

import platform.Foundation.NSLog
import platform.Foundation.NSString

actual fun logDebug(message: String) {
    log("[$LOG_TAG] $message")
}

actual fun logWarning(message: String, cause: Throwable?) {
    log("[$LOG_TAG] $message" + (cause?.let { ": $it" } ?: ""))
}

// A Kotlin String passed through NSLog's varargs is no ObjC object and crashes it; an NSString is.
@Suppress("CAST_NEVER_SUCCEEDS")
private fun log(line: String) = NSLog("%@", line as NSString)
