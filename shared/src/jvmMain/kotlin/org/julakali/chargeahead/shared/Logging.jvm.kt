package org.julakali.chargeahead.shared

actual fun logDebug(message: String) {
    println("[$LOG_TAG] $message")
}

actual fun logWarning(message: String, cause: Throwable?) {
    println("[$LOG_TAG] $message${cause?.let { ": $it" } ?: ""}")
}
