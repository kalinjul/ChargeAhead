package org.julakali.chargeahead.shared

import kotlin.test.Test

/** Logging is diagnostics: whatever the message holds, it never takes the app down. */
class LoggingTest {

    @Test
    fun anyMessageCanBeLogged() {
        logDebug("GarageObserver: first result after 12 ms for Params(unused=kotlin.Unit)")
        logDebug("100% sure, %@ and %d are just text here")
        logWarning("a warning with a cause", IllegalStateException("broken"))
    }
}
