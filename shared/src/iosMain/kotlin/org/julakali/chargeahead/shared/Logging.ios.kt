package org.julakali.chargeahead.shared

import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.create

actual fun logDebug(message: String) {
    log("[$LOG_TAG] $message")
}

actual fun logWarning(message: String, cause: Throwable?) {
    log("[$LOG_TAG] $message" + (cause?.let { ": $it" } ?: ""))
}

// Varargs pass a Kotlin String as a C string, and %@ needs an object; %s would mangle umlauts.
private fun log(line: String) = NSLog("%@", NSString.create(string = line))
