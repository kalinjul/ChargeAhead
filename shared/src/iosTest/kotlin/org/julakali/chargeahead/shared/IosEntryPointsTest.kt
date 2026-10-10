package org.julakali.chargeahead.shared

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The app's graph is started once by the app delegate; nothing builds it behind a caller's back. */
class IosEntryPointsTest {

    @Test
    fun aFeatureBeforeTheStartSaysTheStartIsMissing() {
        val failure = assertFailsWith<IllegalStateException> { createChargeStopsFeature() }
        assertTrue(failure.message!!.contains("startChargeAhead"), failure.message)
    }

    @Test
    fun withoutABackendTheAppDoesNotStart() {
        assertFailsWith<IllegalArgumentException> { startChargeAhead(backendBaseUrl = null, backendToken = "token") }
        assertFailsWith<IllegalArgumentException> { startChargeAhead(backendBaseUrl = "https://example.org", backendToken = null) }
        // A refused start leaves nothing behind.
        assertFailsWith<IllegalStateException> { createChargeStopsFeature() }
    }
}
