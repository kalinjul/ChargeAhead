package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.julakali.chargeahead.android.phone.DrawerContent
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.etaText
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.ui.DrawerUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Numbers and clock times follow the device locale; the suite runs German, like the app. */
@RunWith(RobolectricTestRunner::class)
class FormattingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun drawer(filters: ChargeFilters) =
        compose.setThemedContent { DrawerContent(DrawerUiState(filters = filters), onOpen = {}, onFilters = {}, onModeSelected = {}) }

    @Test
    fun `clock times are 24h in German`() {
        assertEquals("16:00", etaText(compose.activity, 90.0, Fixtures.now))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `clock times follow a 12h locale`() {
        assertEquals("4:00 PM", etaText(compose.activity, 90.0, Fixtures.now))
    }

    @Test
    fun `power steps come from resources`() {
        drawer(ChargeFilters())
        listOf(50, 150, 300).forEach {
            compose.onNodeWithText(compose.string(R.string.drawer_power_step, it)).assertIsDisplayed()
        }
    }
}
