package org.julakali.chargeahead.uitests.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.julakali.chargeahead.android.phone.PhoneApp
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.uitests.setThemedContent
import org.julakali.chargeahead.uitests.string
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The phone shell end to end, on the faked graph of [PhoneAppHarness]. */
@RunWith(RobolectricTestRunner::class)
class ShellFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val harness = PhoneAppHarness()

    @After
    fun tearDown() = harness.stop()

    private fun launch(withVehicle: Boolean = true) {
        harness.start(withVehicle)
        compose.setThemedContent { PhoneApp(librariesRes = 0) }
    }

    private fun searchHint() = compose.string(R.string.home_search_hint)
    private fun chargeNowPill() = compose.string(R.string.home_pill_charge_now)

    private fun countOf(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(WAIT_MILLIS) { countOf(text, substring) > 0 }

    private fun waitForTextGone(text: String, substring: Boolean = false) =
        compose.waitUntil(WAIT_MILLIS) { countOf(text, substring) == 0 }

    /** Search "Münch", pick the hit; leaves the shell in trip mode (or the snackbar, without a vehicle). */
    private fun searchAndPick() {
        compose.onNodeWithText(searchHint()).performClick()
        compose.onNodeWithText(searchHint()).performTextInput("Münch")
        waitForText("München")
        compose.onNodeWithText("München").performClick()
    }

    private fun waitForTrip() {
        // The header label is "München, Bayern"; the pills are the browsing tell.
        waitForTextGone(chargeNowPill())
        waitForText("München", substring = true)
    }

    private fun pressBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `typing a destination and picking it plans a trip`() {
        launch()
        searchAndPick()
        waitForTrip()

        compose.onNodeWithText("München", substring = true).assertIsDisplayed()
        compose.onNodeWithText("km", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Stopp", substring = true).assertIsDisplayed()
        compose.onNodeWithText(" laden", substring = true).assertIsDisplayed()
        compose.onNodeWithText(chargeNowPill()).assertDoesNotExist()
    }

    @Test
    fun `the x on the header drops the trip`() {
        launch()
        searchAndPick()
        waitForTrip()

        compose.onNodeWithContentDescription(compose.string(R.string.home_trip_clear)).performClick()

        waitForText(chargeNowPill())
        compose.onNodeWithText("München", substring = true).assertDoesNotExist()
        compose.onNodeWithText(searchHint()).assertIsDisplayed()
    }

    @Test
    fun `back peels search then trip`() {
        launch()
        compose.onNodeWithText(searchHint()).performClick()
        compose.onNodeWithText(searchHint()).performTextInput("Münch")
        waitForText("München")

        pressBack()
        waitForTextGone("München")
        compose.onNodeWithText(chargeNowPill()).assertIsDisplayed()

        searchAndPick()
        waitForTrip()

        pressBack()
        waitForText(chargeNowPill())
        compose.onNodeWithText("München", substring = true).assertDoesNotExist()
    }

    @Test
    fun `no vehicle shows the snackbar with the garage action`() {
        launch(withVehicle = false)
        searchAndPick()

        waitForText(compose.string(R.string.plan_vehicle_missing))
        compose.onNodeWithText(compose.string(R.string.plan_vehicle_missing_action)).assertIsDisplayed()
        compose.onNodeWithText(chargeNowPill()).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.string(R.string.home_trip_clear)).assertDoesNotExist()
    }

    @Test
    fun `the layout toggle hides the drag handle in tiles`() {
        launch()
        searchAndPick()
        waitForTrip()

        compose.onNodeWithContentDescription(compose.string(R.string.trip_layout_tiles)).performClick()
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithText("an ", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(compose.string(R.string.trip_layout_list)).assertIsDisplayed()

        compose.onNodeWithContentDescription(compose.string(R.string.trip_layout_list)).performClick()
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithText("an ", substring = true).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription(compose.string(R.string.trip_layout_tiles)).assertIsDisplayed()
    }

    @Test
    fun `neu planen reopens the search with the destination picked`() {
        launch()
        searchAndPick()
        waitForTrip()

        compose.onNodeWithText(compose.string(R.string.trip_replan)).performClick()

        // The bar holds the picked label, the panel exactly that one row.
        waitForText("München, Bayern")
        compose.waitUntil(WAIT_MILLIS) { countOf("München") == 1 }
        compose.onNodeWithText("München").performClick()
        waitForTrip()
        compose.onNodeWithContentDescription(compose.string(R.string.home_trip_clear)).assertIsDisplayed()
    }

    @Test
    fun `favoriten opens the routes sheet and back closes it`() {
        launch()
        compose.onNodeWithText(compose.string(R.string.home_pill_favorites)).performClick()

        waitForText(compose.string(R.string.routes_title))
        compose.onNodeWithText(compose.string(R.string.routes_empty)).assertIsDisplayed()

        pressBack()
        waitForTextGone(compose.string(R.string.routes_title))
        assertEquals(0, countOf(compose.string(R.string.routes_empty)))
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
