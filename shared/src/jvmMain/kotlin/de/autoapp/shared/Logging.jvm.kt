package de.autoapp.shared

actual fun logWarning(message: String, cause: Throwable?) {
    println("[$LOG_TAG] $message${cause?.let { ": $it" } ?: ""}")
}
