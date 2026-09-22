package org.julakali.chargeahead.uitests.shell

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import org.julakali.chargeahead.shared.core.MapsHandoff
import org.julakali.chargeahead.shared.domain.ChargeSite

/** The phone shell end to end, on the faked graph of [PhoneAppHarness]. */
@RunWith(RobolectricTestRunner::class)
// Robolectric's default screen is 320x470 dp; the trip sheet's peek is a third of that.
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
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

    /** Tiles keep the action row inside the peek, so it can be tapped without expanding the sheet. */
    private fun showTiles() {
        compose.onNodeWithContentDescription(compose.string(R.string.trip_layout_tiles)).performClick()
        // The toggle flips its label once the tiles are in.
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithContentDescription(compose.string(R.string.trip_layout_list)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The stop rows the planner actually placed, in trip order, by their fake site. */
    private fun plannedSites(): List<ChargeSite> =
        harness.sites.filter { site -> countOf(site.operator!!, substring = true) > 0 }

    private fun nextStartedUrl(): String {
        val intent = Shadows.shadowOf(compose.activity).nextStartedActivity
        assertNotNull("no activity was started", intent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        return intent.dataString!!
    }

    @Test
    fun `an maps senden hands the whole trip to google maps`() {
        launch()
        searchAndPick()
        waitForTrip()
        showTiles()
        val stops = plannedSites()
        assertTrue("the planner placed no stops", stops.isNotEmpty())

        compose.onNodeWithText(compose.string(R.string.trip_send_maps)).performClick()

        val url = nextStartedUrl()
        // Origin stays "my location", the stops ride along as waypoints, München is the destination.
        assertEquals(MapsHandoff.directionsUrl(origin = null, destination = harness.muenchen, waypoints = stops.map { it.position }), url)
        waitForText(compose.string(R.string.trip_maps_sent))
    }

    @Test
    fun `a picked section sends only that stretch`() {
        launch()
        searchAndPick()
        waitForTrip()
        showTiles()
        val stops = plannedSites()
        assertTrue("need two stops for a section", stops.size >= 2)

        compose.onNodeWithText(compose.string(R.string.trip_select_section)).performClick()
        waitForText(compose.string(R.string.trip_section_hint))
        compose.onNodeWithText(stops[0].operator!!, substring = true).performClick()
        waitForText(compose.string(R.string.trip_section_hint_second))
        compose.onNodeWithText(stops[1].operator!!, substring = true).performClick()
        compose.onNodeWithText(compose.string(R.string.trip_send_maps)).performClick()

        // From stop 1 to stop 2: the first point becomes a waypoint, the last the destination.
        assertEquals(MapsHandoff.directionsUrl(origin = null, destination = stops[1].position, waypoints = listOf(stops[0].position)), nextStartedUrl())
        // Selection mode ends with the hand-off.
        waitForText(compose.string(R.string.trip_select_section))
    }

    @Test
    fun `navigation starten from a stop's detail sheet opens a geo uri for that site`() {
        launch()
        searchAndPick()
        waitForTrip()
        showTiles()
        val stop = plannedSites().first()

        compose.onNodeWithText(stop.operator!!, substring = true).performClick()
        waitForText(compose.string(R.string.phone_detail_navigate))
        compose.onNodeWithText(compose.string(R.string.phone_detail_navigate)).performClick()

        val url = nextStartedUrl()
        assertTrue("expected a geo: uri, got $url", url.startsWith("geo:${stop.position.lat},${stop.position.lon}"))
        assertTrue("the label should name the site: $url", url.contains(Uri.encode(stop.name)))
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
