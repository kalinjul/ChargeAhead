package org.julakali.chargeahead.shared

import android.util.Log

actual fun logWarning(message: String, cause: Throwable?) {
    if (cause == null) {
        Log.w(LOG_TAG, message)
    } else {
        Log.w(LOG_TAG, "$message: $cause", cause)
    }
}
