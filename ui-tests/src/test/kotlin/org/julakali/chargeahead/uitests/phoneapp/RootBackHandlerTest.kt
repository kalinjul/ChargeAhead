package org.julakali.chargeahead.uitests.phoneapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.julakali.chargeahead.android.phone.ChargeNow
import org.julakali.chargeahead.android.phone.Home
import org.julakali.chargeahead.android.phone.PhoneNavigator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RootBackHandlerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** The whole app sits in the handler's parent scope; a push must not redraw it. */
    @Test
    fun `a push leaves the parent composition alone`() {
        val navigator = PhoneNavigator(NavBackStack<NavKey>(Home))
        var parentCompositions = 0
        compose.setContent {
            parentCompositions++
            navigator.RootBackHandler()
        }
        compose.waitForIdle()

        compose.runOnIdle { navigator.open(ChargeNow) }
        compose.waitForIdle()

        assertEquals(1, parentCompositions)
    }
}
