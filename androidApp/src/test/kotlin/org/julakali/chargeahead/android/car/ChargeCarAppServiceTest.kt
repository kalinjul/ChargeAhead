package org.julakali.chargeahead.android.car

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// The manifest's Application would start the production Koin graph; nothing here needs it.
@Config(application = Application::class)
class ChargeCarAppServiceTest {

    /** Any app on the phone can bind an exported service; only Google's hosts may act as the car. */
    @Test
    fun `only known car hosts may bind`() {
        val service = Robolectric.setupService(ChargeCarAppService::class.java)

        val allowed = service.createHostValidator().allowedHosts

        assertFalse("an empty allowlist is allow-all in disguise", allowed.isEmpty())
        assertTrue(allowed.containsKey("com.google.android.projection.gearhead"))
    }
}
