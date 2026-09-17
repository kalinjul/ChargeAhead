package org.julakali.chargeahead.shared

/** Logs an error that does not abort the app. */
expect fun logWarning(message: String, cause: Throwable? = null)

/** Shared tag across all platforms' logs. */
const val LOG_TAG = "ChargeAhead"
