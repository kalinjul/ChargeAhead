package de.autoapp.shared

/**
 * Logs an error that does not abort the app.
 *
 * There are quite a few of these: a source doesn't respond, a route can't be
 * computed, the head unit provides no charge level. All of them are caught
 * deliberately because the app should keep running — but none may be
 * swallowed silently. Without these lines, there's no way to find out in the
 * car why the list stays empty.
 */
expect fun logWarning(message: String, cause: Throwable? = null)

/** Shared tag across all platforms' logs. */
const val LOG_TAG = "autoapp"
