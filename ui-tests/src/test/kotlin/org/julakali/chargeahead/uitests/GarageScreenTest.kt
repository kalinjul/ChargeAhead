package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.GarageScreen
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.ui.GarageUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.garage_add_title
import org.julakali.chargeahead.shared.resources.garage_arrival_title
import org.julakali.chargeahead.shared.resources.garage_empty_title
import org.julakali.chargeahead.shared.resources.garage_percent
import org.julakali.chargeahead.shared.resources.garage_range_number
import org.julakali.chargeahead.shared.resources.garage_vehicle_row
import org.julakali.chargeahead.shared.resources.soc_dialog_apply

/** A phone's size: on Robolectric's default screen the floating button would sit on the rows. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "+w411dp-h891dp")
class GarageScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val id3 = VehicleProfile("VW ID.3 Pro S", 77.0, 16.5, setOf(ConnectorType.CCS2), dcPeakPowerKw = 175.0)
    private val model3 = VehicleProfile("Tesla Model 3 LR", 75.0, 15.5, setOf(ConnectorType.CCS2), dcPeakPowerKw = 250.0)

    private var selected: VehicleProfile? = null
    private var openedVehicle = false
    private var openedAdd = false
    private var arrivalOpened = false
    private var arrivalChanged: Int? = null
    private var arrivalConfirmed = false

    private fun twoCars(arrivalSheet: Int? = null) = GarageUiState(
        vehicles = listOf(id3, model3),
        selected = id3,
        arrivalSocPercent = 10.0,
        fullRangeKm = mapOf(id3.id to 467.0, model3.id to 484.0),
        arrivalSheet = arrivalSheet,
    )

    /** Lets a test swap the state under a running screen, as the ViewModel would. */
    private var state by mutableStateOf(GarageUiState())

    private fun garage(initial: GarageUiState) {
        state = initial
        compose.setThemedContent {
            GarageScreen(
                uiState = state,
                onSelect = { selected = it },
                onOpenVehicle = { openedVehicle = true },
                onOpenAdd = { openedAdd = true },
                onArrivalSheetOpen = { arrivalOpened = true },
                onArrivalChange = { arrivalChanged = it },
                onArrivalConfirm = { arrivalConfirmed = true },
                onArrivalDismiss = {},
            )
        }
    }

    @Test
    fun `the planning car is the card's subject, its name shown once`() {
        garage(twoCars())

        compose.onNodeWithText(compose.string(Res.string.garage_range_number, 467)).assertIsDisplayed()
        compose.onNodeWithText("16,5").assertIsDisplayed()
        // No second selector: the name is on the card, nowhere else.
        assertEquals(1, compose.onAllNodesWithText(id3.displayName).fetchSemanticsNodes().size)
    }

    /** The cards in the stack, and the ones faded out, are scenery: only the card on top is read out. */
    @Test
    fun `only the card on top is there for a screen reader`() {
        garage(twoCars())

        compose.onNodeWithText(compose.string(Res.string.garage_range_number, 467)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.garage_range_number, 484)).assertDoesNotExist()
    }

    @Test
    fun `swiping the card to the next car plans with it`() {
        garage(twoCars())

        compose.onNodeWithText(compose.string(Res.string.garage_range_number, 467)).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(model3, selected)
    }

    @Test
    fun `past the last car the first one comes round again`() {
        garage(twoCars().copy(selected = model3))
        compose.waitForIdle()

        compose.onNodeWithText(compose.string(Res.string.garage_range_number, 484)).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(id3, selected)
    }

    @Test
    fun `a car picked elsewhere slides its card into view`() {
        garage(twoCars())

        state = state.copy(selected = model3)
        compose.waitForIdle()

        compose.onNodeWithText(compose.string(Res.string.garage_range_number, 484)).assertIsDisplayed()
    }

    /** Removing the car on screen hands the garage a shorter list with the first car selected. */
    @Test
    fun `after a removal the pager rests on the first car, whole`() {
        garage(twoCars().copy(selected = model3))
        compose.waitForIdle()

        state = state.copy(vehicles = listOf(id3), selected = id3)
        compose.waitForIdle()

        val card = compose.onNodeWithText(compose.string(Res.string.garage_range_number, 467)).assertIsDisplayed().getBoundsInRoot()
        // The card's text sits just inside the page inset, not off to one side between two pages.
        assertTrue("card text at ${card.left}", card.left > 18.dp && card.left < 60.dp)
    }

    @Test
    fun `the add button and the vehicle row lead on`() {
        garage(twoCars())

        // As TalkBack finds it: Material leaves the floating button's label to its icon.
        compose.onNodeWithContentDescription(compose.string(Res.string.garage_add_title)).performClick()
        compose.onNodeWithText(compose.string(Res.string.garage_vehicle_row)).performScrollTo().performClick()

        assertTrue(openedAdd)
        assertTrue(openedVehicle)
    }

    @Test
    fun `the arrival row shows the level and opens its sheet`() {
        garage(twoCars())

        compose.onNodeWithText(compose.string(Res.string.garage_percent, 10)).performScrollTo().performClick()

        assertTrue(arrivalOpened)
    }

    @Test
    fun `a pick in the arrival sheet sets the level, Übernehmen applies it`() {
        garage(twoCars(arrivalSheet = 10))

        compose.onNodeWithText(compose.string(Res.string.garage_percent, 30)).performClick()
        compose.onNodeWithText(compose.string(Res.string.soc_dialog_apply)).performClick()

        assertEquals(30, arrivalChanged)
        assertTrue(arrivalConfirmed)
    }

    @Test
    fun `an empty garage offers adding a car once, and still the arrival level`() {
        garage(GarageUiState(arrivalSocPercent = 10.0))

        compose.onNodeWithText(compose.string(Res.string.garage_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.garage_arrival_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.garage_vehicle_row)).assertDoesNotExist()
        // The card's button; no floating one beside it.
        assertEquals(1, compose.onAllNodesWithText(compose.string(Res.string.garage_add_title)).fetchSemanticsNodes().size)
        compose.onNodeWithText(compose.string(Res.string.garage_add_title)).performClick()

        assertTrue(openedAdd)
    }
}
