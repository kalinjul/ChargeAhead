package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.julakali.chargeahead.android.phone.DestinationHeader
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.home_trip_clear

@RunWith(RobolectricTestRunner::class)
class DestinationHeaderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `title and subtitle are shown`() {
        compose.setThemedContent { DestinationHeader("München", "790 km · 9h 0m", onClear = {}, onTitleClick = {}) }
        compose.onNodeWithText("München").assertIsDisplayed()
        compose.onNodeWithText("790 km · 9h 0m").assertIsDisplayed()
    }

    @Test
    fun `the x drops the trip`() {
        var cleared = false
        compose.setThemedContent { DestinationHeader("München", "", onClear = { cleared = true }, onTitleClick = {}) }
        compose.onNodeWithContentDescription(compose.string(Res.string.home_trip_clear)).performClick()
        assertTrue(cleared)
    }

    @Test
    fun `tapping the title asks for the destination`() {
        var flown = false
        compose.setThemedContent { DestinationHeader("München", "", onClear = {}, onTitleClick = { flown = true }) }
        compose.onNodeWithText("München").performClick()
        assertTrue(flown)
    }
}
