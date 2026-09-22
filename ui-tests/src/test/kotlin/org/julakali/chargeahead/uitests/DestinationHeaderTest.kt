package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.julakali.chargeahead.android.phone.DestinationHeader
import org.julakali.chargeahead.android.phone.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DestinationHeaderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `title and subtitle are shown`() {
        compose.setThemedContent { DestinationHeader("München", "790 km · 9 h 0 min · 3 Stopps", onClear = {}) }
        compose.onNodeWithText("München").assertIsDisplayed()
        compose.onNodeWithText("790 km · 9 h 0 min · 3 Stopps").assertIsDisplayed()
    }

    @Test
    fun `the x drops the trip`() {
        var cleared = false
        compose.setThemedContent { DestinationHeader("München", "", onClear = { cleared = true }) }
        compose.onNodeWithContentDescription(compose.string(R.string.home_trip_clear)).performClick()
        assertTrue(cleared)
    }
}
