package org.julakali.chargeahead.uitests.phoneapp

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onFirst
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.android.phone.LocalNow
import org.julakali.chargeahead.android.phone.theme.ChargeAheadTheme
import org.julakali.chargeahead.shared.domain.distanceKmTo
import org.julakali.chargeahead.uitests.Fixtures
import androidx.compose.ui.test.isDisplayed
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDialog
import org.julakali.chargeahead.shared.domain.MapsHandoff
import org.julakali.chargeahead.shared.domain.ChargeSite

/** The phone app end to end, on the faked graph of [PhoneAppHarness]. */
@RunWith(RobolectricTestRunner::class)
// Robolectric's default screen is 320x470 dp; the trip sheet's peek is a third of that.
@Config(qualifiers = "+w411dp-h891dp-xxhdpi")
class PhoneAppFlowTest {
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

    /** Search "Münch", pick the hit; leaves the app in trip mode (or the snackbar, without a vehicle). */
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

    /** The drawer content stays composed while closed, so displayed is the tell, not existence. */
    private fun drawerOpen() = compose.onNodeWithText(compose.string(R.string.drawer_car)).isDisplayed()

    private fun openDrawer() {
        compose.onNodeWithContentDescription(compose.string(R.string.home_settings)).performClick()
        compose.waitUntil(WAIT_MILLIS) { drawerOpen() }
    }

    /** Back goes to the window that has it, which for an open sheet is the sheet's own. */
    private fun pressBack() = compose.runOnUiThread {
        val sheet = ShadowDialog.getLatestDialog() as? ComponentDialog
        val dispatcher = if (sheet?.isShowing == true) sheet.onBackPressedDispatcher else compose.activity.onBackPressedDispatcher
        dispatcher.onBackPressed()
    }

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
    fun `aktive route is dimmed until a trip was sent, then shows it until navigieren beenden`() {
        launch()
        val pill = compose.string(R.string.home_pill_active_route)
        compose.onNodeWithText(pill).assertIsNotEnabled()

        searchAndPick()
        waitForTrip()
        showTiles()
        val stops = plannedSites()
        compose.onNodeWithText(compose.string(R.string.trip_send_maps)).performClick()
        nextStartedUrl()

        // Sending commits: the page is on top, with the same stops.
        waitForText(compose.string(R.string.active_route_end))
        stops.forEach { compose.onNodeWithText(it.operator!!, substring = true).assertIsDisplayed() }

        // Back lands on browsing, the pill is live now and reopens the page.
        pressBack()
        waitForTextGone(compose.string(R.string.active_route_end))
        compose.onNodeWithText(searchHint()).assertIsDisplayed()
        compose.onNodeWithText(pill).assertIsEnabled().performClick()
        waitForText(compose.string(R.string.active_route_end))

        compose.onNodeWithText(compose.string(R.string.active_route_end)).performClick()
        waitForTextGone(compose.string(R.string.active_route_end))
        compose.onNodeWithText(pill).assertIsNotEnabled()
        assertNull(harness.trips.state.value.committed)
    }

    /** ModalDrawerSheet only handles back when it is given the drawer state. */
    @Test
    fun `back closes the drawer`() {
        launch()
        openDrawer()

        pressBack()

        compose.waitUntil(WAIT_MILLIS) { !drawerOpen() }
        compose.onNodeWithText(chargeNowPill()).assertIsDisplayed()
    }

    @Test
    fun `back closes the drawer before it touches the trip`() {
        launch()
        searchAndPick()
        waitForTrip()
        openDrawer()

        pressBack()

        compose.waitUntil(WAIT_MILLIS) { !drawerOpen() }
        compose.onNodeWithText("München", substring = true).assertIsDisplayed()
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
        // Sending commits: the active route page takes over.
        waitForText(compose.string(R.string.active_route_end))
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
        // The whole plan is committed, whatever section went out.
        waitForText(compose.string(R.string.active_route_end))
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

        assertEquals(MapsHandoff.geoUri(stop.position, stop.name), nextStartedUrl())
    }

    @Test
    fun `back closes a stop's detail sheet and leaves the trip in place`() {
        launch()
        searchAndPick()
        waitForTrip()
        showTiles()
        val stop = plannedSites().first()

        compose.onNodeWithText(stop.operator!!, substring = true).performClick()
        waitForText(compose.string(R.string.phone_detail_navigate))

        pressBack()

        waitForTextGone(compose.string(R.string.phone_detail_navigate))
        compose.onNodeWithText("München", substring = true).assertIsDisplayed()
        compose.onNodeWithText(stop.operator!!, substring = true).assertIsDisplayed()
    }

    @Test
    fun `auto in the drawer leads to the garage, where a preset becomes the car`() {
        launch()
        val preset = CATALOG_PRESET_NAME
        openDrawer()

        compose.onNodeWithText(compose.string(R.string.drawer_car)).performClick()
        waitForText(compose.string(R.string.garage_add))

        compose.onNodeWithText(compose.string(R.string.garage_add)).performClick()
        waitForText(preset)
        compose.onNodeWithText(preset).performClick()

        // Picking pops back to the garage, which now lists and uses the preset.
        waitForText(compose.string(R.string.garage_added, preset))
        compose.onNodeWithText(compose.string(R.string.garage_add)).assertIsDisplayed()
        compose.onAllNodesWithText(preset).onFirst().assertIsDisplayed()
        compose.onNodeWithText(compose.string(R.string.garage_no_car_yet)).assertDoesNotExist()
        assertEquals(preset, runBlocking { harness.vehicles.vehicle.first()?.displayName })

        pressBack()
        waitForTextGone(compose.string(R.string.garage_add))
        openDrawer()
        compose.onAllNodesWithText(preset).onFirst().assertIsDisplayed()
    }

    /** The corridor sites are hundreds of km out; "charge now" only looks a few km around the phone. */
    @Test
    fun `jetzt laden lists the chargers nearest first`() {
        // Deliberately not in distance order.
        val nearby = listOf(
            Fixtures.site("n3", "Shell Recharge", harness.hamburg.copy(lat = harness.hamburg.lat + 0.027), "Hamburg"),
            Fixtures.site("n1", "Fastned", harness.hamburg.copy(lat = harness.hamburg.lat + 0.009), "Hamburg"),
            Fixtures.site("n2", "Allego", harness.hamburg.copy(lat = harness.hamburg.lat + 0.018), "Hamburg"),
        )
        harness.start(nearby = nearby)
        compose.setThemedContent { PhoneApp(librariesRes = 0) }

        compose.onNodeWithText(chargeNowPill()).performClick()

        nearby.forEach { waitForText(it.operator!!) }
        val nearestFirst = nearby.sortedBy { it.position.distanceKmTo(harness.hamburg) }
        assertEquals(listOf("Fastned", "Allego", "Shell Recharge"), nearestFirst.map { it.operator })
        val tops = nearestFirst.map { compose.onNodeWithText(it.operator!!).getBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        harness.sites.forEach { compose.onNodeWithText(it.operator!!).assertDoesNotExist() }
    }

    @Test
    fun `a jetzt laden card opens the stop's detail sheet`() {
        val nearby = listOf(Fixtures.site("n1", "Fastned", harness.hamburg.copy(lat = harness.hamburg.lat + 0.009), "Hamburg"))
        harness.start(nearby = nearby)
        compose.setThemedContent { PhoneApp(librariesRes = 0) }
        compose.onNodeWithText(chargeNowPill()).performClick()
        waitForText("Fastned")

        compose.onNodeWithText("Fastned").performClick()

        waitForText(compose.string(R.string.phone_detail_navigate))
    }

    /** Closing and reopening right away must not swallow the tap. */
    @Test
    fun `jetzt laden reopens right after it was closed`() {
        val nearby = listOf(Fixtures.site("n1", "Fastned", harness.hamburg.copy(lat = harness.hamburg.lat + 0.009), "Hamburg"))
        harness.start(nearby = nearby)
        compose.setThemedContent { PhoneApp(librariesRes = 0) }

        // The pill and the sheet share their label; the card is the sheet's tell.
        compose.onNodeWithText(chargeNowPill()).performClick()
        waitForText("Fastned")
        pressBack()
        waitForTextGone("Fastned")

        compose.onNodeWithText(chargeNowPill()).performClick()

        waitForText("Fastned")
    }

    /** The search ViewModels outlive the activity, so a rotation keeps the typed text and the open search. */
    @Test
    fun `recreating the activity keeps the search open with its text`() {
        launch()
        compose.onNodeWithText(searchHint()).performClick()
        compose.onNodeWithText(searchHint()).performTextInput("Münch")
        waitForText("München")

        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(LocalNow provides { Fixtures.now }) {
                    ChargeAheadTheme { PhoneApp(librariesRes = 0) }
                }
            }
        }

        waitForText("Münch")
        waitForText("München")
        compose.onNodeWithContentDescription(compose.string(R.string.home_search_back)).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
