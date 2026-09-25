package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.ui.TripListLayout
import org.julakali.chargeahead.android.phone.TripSummary
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripSummaryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun summary(
        layout: TripListLayout = TripListLayout.LIST,
        onToggleLayout: () -> Unit = {},
        onReplan: () -> Unit = {},
    ) = compose.setThemedContent { TripSummary(Fixtures.plan, layout, onToggleLayout, onReplan) }

    @Test
    fun `the charging total is shown`() {
        summary()
        compose.onNodeWithText(compose.string(R.string.trip_summary_charging, "1 h 15 min")).assertIsDisplayed()
    }

    @Test
    fun `replan reopens the search`() {
        var replanned = false
        summary(onReplan = { replanned = true })
        compose.onNodeWithText(compose.string(R.string.trip_replan)).performClick()
        assertTrue(replanned)
    }

    @Test
    fun `the layout toggle offers tiles from the list`() {
        var toggled = false
        summary(layout = TripListLayout.LIST, onToggleLayout = { toggled = true })
        compose.onNodeWithContentDescription(compose.string(R.string.trip_layout_tiles)).assertIsDisplayed().performClick()
        assertTrue(toggled)
        compose.onNodeWithContentDescription(compose.string(R.string.trip_layout_list)).assertDoesNotExist()
    }

    @Test
    fun `the layout toggle offers the list from tiles`() {
        summary(layout = TripListLayout.TILES)
        compose.onNodeWithContentDescription(compose.string(R.string.trip_layout_list)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.string(R.string.trip_layout_tiles)).assertDoesNotExist()
    }
}
