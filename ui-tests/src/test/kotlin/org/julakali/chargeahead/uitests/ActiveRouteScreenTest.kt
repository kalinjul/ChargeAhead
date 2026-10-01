package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.julakali.chargeahead.android.phone.ActiveRouteScreen
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.SectionSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "+w600dp-h1000dp")
class ActiveRouteScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Calls {
        var openedStop: PlannedStop? = null
        var sentToMaps = false
        var replanned = false
        var ended = false
        var toggledSelecting = false
    }

    private fun screen(selection: SectionSelection = SectionSelection(), socInput: String? = null): Calls {
        val calls = Calls()
        compose.setThemedContent {
            Box(Modifier.fillMaxSize()) {
                ActiveRouteScreen(
                    trip = CommittedTrip(Fixtures.plan, startSocPercent = 26.0, committedAtEpochMillis = 0L),
                    selection = selection,
                    planning = false,
                    onToggleSelecting = { calls.toggledSelecting = true },
                    onPickPoint = {},
                    onSectionSent = {},
                    onOpenStop = { calls.openedStop = it },
                    onSendToMaps = { calls.sentToMaps = true },
                    onReplan = { calls.replanned = true },
                    onEnd = { calls.ended = true },
                    socInput = socInput,
                )
            }
        }
        return calls
    }

    @Test
    fun `the summary card sums the trip up and every stop is listed`() {
        screen()
        // The page bar carries the name; here only the destination row does.
        compose.onAllNodesWithText("München").assertCountEquals(1)
        compose.onNodeWithText("790 km · 9h 0m · 3 Stopps").assertIsDisplayed()
        Fixtures.plan.stops.forEach { stop ->
            compose.onNodeWithText(stop.site.operator!!, substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `the start row has no charge-level pen on the active route`() {
        screen()
        compose.onNodeWithContentDescription(compose.string(R.string.trip_soc_edit)).assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.string(R.string.trip_arrival_soc_edit)).assertDoesNotExist()
    }

    @Test
    fun `tapping a stop opens it`() {
        val calls = screen()
        compose.onNodeWithText(Fixtures.stopKassel.site.operator!!, substring = true).performClick()
        assertEquals(Fixtures.stopKassel, calls.openedStop)
    }

    @Test
    fun `the buttons do what they say`() {
        val calls = screen()
        compose.onNodeWithText(compose.string(R.string.trip_send_maps)).performClick()
        assertTrue(calls.sentToMaps)
        compose.onNodeWithText(compose.string(R.string.trip_select_section)).performClick()
        assertTrue(calls.toggledSelecting)
        compose.onNodeWithText(compose.string(R.string.trip_replan)).performClick()
        assertTrue(calls.replanned)
        compose.onNodeWithText(compose.string(R.string.active_route_end)).performClick()
        assertTrue(calls.ended)
    }

    @Test
    fun `the charge-level prompt says why it is asking`() {
        screen(socInput = "42")
        compose.onNodeWithText(compose.string(R.string.soc_dialog_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(R.string.soc_dialog_car_silent)).assertIsDisplayed()
    }
}
