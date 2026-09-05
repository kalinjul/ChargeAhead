package de.autoapp.shared

import android.util.Log

actual fun logWarning(message: String, cause: Throwable?) {
    // Cause is included both in the text AND as a Throwable: depending on the
    // log buffer, the stack trace doesn't always make it into logcat, but the text does.
    if (cause == null) {
        Log.w(LOG_TAG, message)
    } else {
        Log.w(LOG_TAG, "$message: $cause", cause)
    }
}
