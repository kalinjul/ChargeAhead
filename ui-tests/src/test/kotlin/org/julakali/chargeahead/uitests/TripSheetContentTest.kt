package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.julakali.chargeahead.shared.ui.TripListLayout
import org.julakali.chargeahead.android.phone.SocEditing
import org.julakali.chargeahead.android.phone.TripSheetContent
import org.julakali.chargeahead.android.phone.etaText
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.SectionSelection
import org.julakali.chargeahead.shared.domain.TripPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.garage_kw
import org.julakali.chargeahead.shared.resources.garage_percent
import org.julakali.chargeahead.shared.resources.trip_duration_minutes
import org.julakali.chargeahead.shared.resources.trip_section_hint
import org.julakali.chargeahead.shared.resources.trip_select_cancel
import org.julakali.chargeahead.shared.resources.trip_select_section
import org.julakali.chargeahead.shared.resources.trip_send_maps
import org.julakali.chargeahead.shared.resources.trip_start
import org.julakali.chargeahead.shared.resources.trip_tile_arrival

/** Tall and wide enough that every row and tile is on screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "+w600dp-h1000dp")
class TripSheetContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Calls {
        var openedStop: PlannedStop? = null
        var pickedPoint: Int? = null
        var editStartSoc = false
        var editArrivalSoc = false
        var sentToMaps = false
        var sectionSent = false
        var toggledSelecting = false
        var startSocTyped: String? = null
        var arrivalSocTyped: String? = null
    }

    private fun sheet(
        plan: TripPlan = Fixtures.plan,
        layout: TripListLayout = TripListLayout.LIST,
        selection: SectionSelection = SectionSelection(),
        socInput: String? = null,
        arrivalSocInput: String? = null,
    ): Calls {
        val calls = Calls()
        compose.setThemedContent {
            Box(Modifier.fillMaxSize()) {
                TripSheetContent(
                    plan = plan,
                    startSocPercent = 26.0,
                    layout = layout,
                    selection = selection,
                    onToggleSelecting = { calls.toggledSelecting = true },
                    onPickPoint = { calls.pickedPoint = it },
                    onSectionSent = { calls.sectionSent = true },
                    onOpenStop = { calls.openedStop = it },
                    onSendToMaps = { calls.sentToMaps = true },
                    socEditing = SocEditing(
                        socInput = socInput,
                        arrivalSocInput = arrivalSocInput,
                        askedForReplan = false,
                        onEditStartSoc = { calls.editStartSoc = true },
                        onSocInputChange = { calls.startSocTyped = it },
                        onSocConfirm = {},
                        onSocDismiss = {},
                        onEditArrivalSoc = { calls.editArrivalSoc = true },
                        onArrivalSocInputChange = { calls.arrivalSocTyped = it },
                        onArrivalSocConfirm = {},
                        onArrivalSocDismiss = {},
                    ),
                )
            }
        }
        return calls
    }

    private val stops get() = Fixtures.plan.stops

    // LIST

    @Test
    fun `start row shows the departure and the charge level`() {
        sheet()
        compose.onNodeWithText("14:30").assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.garage_percent, 26)).assertIsDisplayed()
    }

    @Test
    fun `start row leads with the start address when the plan has one`() {
        sheet(plan = Fixtures.plan.copy(startAddress = "Feldstraße 66, Hamburg"))
        compose.onNodeWithText("Feldstraße 66, Hamburg").assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.trip_start)).assertDoesNotExist()
    }

    @Test
    fun `every stop shows operator, town, arrival, levels, power and charging time`() {
        sheet()
        stops.forEach { stop ->
            compose.onNodeWithText(stop.site.operator!!).assertIsDisplayed()
            compose.onNodeWithText(stop.site.address!!.town!!).assertIsDisplayed()
            compose.onNodeWithText(etaText(compose.activity, stop.arrivalMinutesFromStart, Fixtures.now)).assertIsDisplayed()
            listOf(stop.arrivalSocPercent, stop.departureSocPercent).forEach { level ->
                compose.onAllNodesWithText(compose.string(Res.string.garage_percent, level.toInt())).onFirst().assertIsDisplayed()
            }
            compose.onNodeWithText(compose.string(Res.string.garage_kw, stop.maxPowerKw.toInt())).assertIsDisplayed()
            compose.onNodeWithText(compose.string(Res.string.trip_duration_minutes, stop.chargeMinutes.toInt())).assertIsDisplayed()
        }
    }

    @Test
    fun `first stop arrives at 16 00 on the pinned clock`() {
        sheet()
        compose.onNodeWithText("16:00").assertIsDisplayed()
    }

    @Test
    fun `no departure time on a stop`() {
        sheet()
        compose.onNodeWithText("16:30", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the drive to each row stands above it`() {
        sheet()
        listOf("1h 30m · 150 km", "2h 5m · 170 km", "3h 10m · 300 km", "1h 0m · 170 km").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun `destination row shows the arrival and the level left`() {
        sheet()
        compose.onNodeWithText("23:30").assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.garage_percent, 20)).assertIsDisplayed()
    }

    @Test
    fun `tapping a stop opens it`() {
        val calls = sheet()
        compose.onNodeWithText("EnBW").performClick()
        assertEquals(stops[1], calls.openedStop)
    }

    @Test
    fun `tapping the start edits the charge level`() {
        val calls = sheet()
        compose.onNodeWithText(compose.string(Res.string.trip_start)).performClick()
        assertTrue(calls.editStartSoc)
    }

    @Test
    fun `tapping the destination edits the arrival level`() {
        val calls = sheet()
        compose.onNodeWithText("München").performClick()
        assertTrue(calls.editArrivalSoc)
    }

    /** Both levels are edited in the garage's charge-level sheet: slider and the usual picks. */
    @Test
    fun `the start level offers its quick picks`() {
        val calls = sheet(socInput = "42")

        compose.onNodeWithText(compose.string(Res.string.garage_percent, 40)).performClick()

        assertEquals("40", calls.startSocTyped)
    }

    @Test
    fun `the arrival level offers its quick picks`() {
        val calls = sheet(arrivalSocInput = "10")

        compose.onNodeWithText(compose.string(Res.string.garage_percent, 30)).performClick()

        assertEquals("30", calls.arrivalSocTyped)
    }

    @Test
    fun `in section mode a stop tap picks the point instead of opening it`() {
        val calls = sheet(selection = SectionSelection(selecting = true))
        compose.onNodeWithText("Ionity").performClick()
        assertEquals(1, calls.pickedPoint)
        assertNull(calls.openedStop)
    }

    @Test
    fun `the section hint shows while selecting`() {
        sheet(selection = SectionSelection(selecting = true))
        compose.onNodeWithText(compose.string(Res.string.trip_section_hint)).assertIsDisplayed()
    }

    @Test
    fun `no section hint outside selection`() {
        sheet()
        compose.onNodeWithText(compose.string(Res.string.trip_section_hint)).assertDoesNotExist()
    }

    @Test
    fun `send to maps hands over the trip and ends the section`() {
        val calls = sheet()
        compose.onNodeWithText(compose.string(Res.string.trip_send_maps)).performClick()
        assertTrue(calls.sentToMaps)
        assertTrue(calls.sectionSent)
    }

    @Test
    fun `the section button toggles selecting`() {
        val calls = sheet()
        compose.onNodeWithText(compose.string(Res.string.trip_select_section)).performClick()
        assertTrue(calls.toggledSelecting)
    }

    @Test
    fun `while selecting the section button reads cancel`() {
        sheet(selection = SectionSelection(selecting = true))
        compose.onNodeWithText(compose.string(Res.string.trip_select_cancel)).assertIsDisplayed()
    }

    // TILES

    @Test
    fun `tiles show operator and arrival`() {
        sheet(layout = TripListLayout.TILES)
        stops.forEach { stop ->
            compose.onNodeWithText(stop.site.operator!!).assertIsDisplayed()
            compose.onNodeWithText(
                compose.string(Res.string.trip_tile_arrival, etaText(compose.activity, stop.arrivalMinutesFromStart, Fixtures.now)),
            ).assertIsDisplayed()
        }
    }

    @Test
    fun `tapping a tile opens the stop`() {
        val calls = sheet(layout = TripListLayout.TILES)
        compose.onNodeWithText("Aral pulse").performClick()
        assertEquals(stops[2], calls.openedStop)
    }

    @Test
    fun `tiles keep the action row`() {
        sheet(layout = TripListLayout.TILES)
        compose.onNodeWithText(compose.string(Res.string.trip_send_maps)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.trip_select_section)).assertIsDisplayed()
    }
}
