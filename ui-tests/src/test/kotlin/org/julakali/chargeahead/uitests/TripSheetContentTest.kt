package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.TripListLayout
import org.julakali.chargeahead.android.phone.TripSheetContent
import org.julakali.chargeahead.android.phone.etaText
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.ui.SectionSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tall and wide enough that every row and tile is on screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp-h1000dp")
class TripSheetContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Calls {
        var openedStop: PlannedStop? = null
        var pickedPoint: Int? = null
        var editStartSoc = false
        var editArrivalSoc = false
        var sentUrl: String? = null
        var sectionSent = false
        var toggledSave = false
        var toggledSelecting = false
    }

    private fun sheet(
        layout: TripListLayout = TripListLayout.LIST,
        selection: SectionSelection = SectionSelection(),
        isSaved: Boolean = false,
    ): Calls {
        val calls = Calls()
        compose.setThemedContent {
            Box(Modifier.fillMaxSize()) {
                TripSheetContent(
                    plan = Fixtures.plan,
                    startPosition = Fixtures.hamburg,
                    startSocPercent = 26.0,
                    isSaved = isSaved,
                    layout = layout,
                    selection = selection,
                    socInput = null,
                    arrivalSocInput = null,
                    onToggleSelecting = { calls.toggledSelecting = true },
                    onPickPoint = { calls.pickedPoint = it },
                    onSectionSent = { calls.sectionSent = true },
                    onOpenStop = { calls.openedStop = it },
                    onSendToMaps = { calls.sentUrl = it },
                    onToggleSave = { calls.toggledSave = true },
                    onEditStartSoc = { calls.editStartSoc = true },
                    onSocInputChange = {},
                    onSocConfirm = {},
                    onSocDismiss = {},
                    onEditArrivalSoc = { calls.editArrivalSoc = true },
                    onArrivalSocInputChange = {},
                    onArrivalSocConfirm = {},
                    onArrivalSocDismiss = {},
                )
            }
        }
        return calls
    }

    private val stops get() = Fixtures.plan.stops

    // LIST

    @Test
    fun `start row shows the charge level`() {
        sheet()
        compose.onNodeWithText(compose.string(R.string.trip_dep_now, 26)).assertIsDisplayed()
    }

    @Test
    fun `every stop shows operator, charge and times`() {
        sheet()
        stops.forEach { stop ->
            compose.onNodeWithText(stop.site.operator!!).assertIsDisplayed()
            compose.onNodeWithText(
                compose.string(
                    R.string.trip_stop_charge,
                    stop.maxPowerKw.toInt(),
                    stop.arrivalSocPercent.toInt(),
                    stop.departureSocPercent.toInt(),
                ),
            ).assertIsDisplayed()
            compose.onNodeWithText(
                compose.string(
                    R.string.trip_stop_times,
                    etaText(stop.arrivalMinutesFromStart, Fixtures.now),
                    etaText(stop.arrivalMinutesFromStart + stop.chargeMinutes, Fixtures.now),
                ),
            ).assertIsDisplayed()
        }
    }

    @Test
    fun `first stop arrives at 16 00 on the pinned clock`() {
        sheet()
        compose.onNodeWithText(compose.string(R.string.trip_stop_times, "16:00", "16:30")).assertIsDisplayed()
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
        compose.onNodeWithText(compose.string(R.string.trip_start)).performClick()
        assertTrue(calls.editStartSoc)
    }

    @Test
    fun `tapping the destination edits the arrival level`() {
        val calls = sheet()
        compose.onNodeWithText("München").performClick()
        assertTrue(calls.editArrivalSoc)
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
        compose.onNodeWithText(compose.string(R.string.trip_section_hint)).assertIsDisplayed()
    }

    @Test
    fun `no section hint outside selection`() {
        sheet()
        compose.onNodeWithText(compose.string(R.string.trip_section_hint)).assertDoesNotExist()
    }

    @Test
    fun `send to maps hands over a directions url`() {
        val calls = sheet()
        compose.onNodeWithText(compose.string(R.string.trip_send_maps)).performClick()
        assertTrue(calls.sentUrl.orEmpty().startsWith("https://www.google.com/maps/dir/"))
        assertTrue(calls.sectionSent)
    }

    @Test
    fun `the heart toggles saving`() {
        val calls = sheet()
        compose.onNodeWithContentDescription(compose.string(R.string.trip_save)).performClick()
        assertTrue(calls.toggledSave)
    }

    @Test
    fun `the section button toggles selecting`() {
        val calls = sheet()
        compose.onNodeWithText(compose.string(R.string.trip_select_section)).performClick()
        assertTrue(calls.toggledSelecting)
    }

    @Test
    fun `while selecting the section button reads cancel`() {
        sheet(selection = SectionSelection(selecting = true))
        compose.onNodeWithText(compose.string(R.string.trip_select_cancel)).assertIsDisplayed()
    }

    // TILES

    @Test
    fun `tiles show operator and arrival`() {
        sheet(layout = TripListLayout.TILES)
        stops.forEach { stop ->
            compose.onNodeWithText(stop.site.operator!!).assertIsDisplayed()
            compose.onNodeWithText(
                compose.string(R.string.trip_tile_arrival, etaText(stop.arrivalMinutesFromStart, Fixtures.now)),
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
        compose.onNodeWithText(compose.string(R.string.trip_send_maps)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(R.string.trip_select_section)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.string(R.string.trip_save)).assertIsDisplayed()
    }
}
