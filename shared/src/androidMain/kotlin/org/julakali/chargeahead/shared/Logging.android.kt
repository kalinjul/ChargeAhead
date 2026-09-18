package org.julakali.chargeahead.shared

import android.util.Log

actual fun logDebug(message: String) {
    Log.d(LOG_TAG, message)
}

actual fun logWarning(message: String, cause: Throwable?) {
    if (cause == null) {
        Log.w(LOG_TAG, message)
    } else {
        Log.w(LOG_TAG, "$message: $cause", cause)
    }
}
