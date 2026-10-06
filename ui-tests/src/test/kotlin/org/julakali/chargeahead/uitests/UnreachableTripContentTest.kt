package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.julakali.chargeahead.android.phone.SocEditing
import org.julakali.chargeahead.android.phone.UnreachableTripContent
import org.julakali.chargeahead.shared.domain.UnreachableTrip
import org.julakali.chargeahead.shared.ui.TripUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.plan_failed_no_charger
import org.julakali.chargeahead.shared.resources.plan_failed_no_charger_at_start
import org.julakali.chargeahead.shared.resources.plan_failed_no_connection
import org.julakali.chargeahead.shared.resources.plan_failed_no_route
import org.julakali.chargeahead.shared.resources.trip_dep_now
import org.julakali.chargeahead.shared.resources.trip_send_maps

/** No plan came out: the sheet says why and keeps both levels a tap away. */
@RunWith(RobolectricTestRunner::class)
class UnreachableTripContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var editStart = false
    private var editArrival = false

    private fun content(why: UnreachableTrip = UnreachableTrip.NoCharger(afterKm = 0.0)) = compose.setThemedContent {
        UnreachableTripContent(
            trip = TripUiState.Unreachable(Fixtures.plan.destination, why = why, startSocPercent = 5.0),
            socEditing = SocEditing(
                socInput = null,
                arrivalSocInput = null,
                askedForReplan = false,
                onEditStartSoc = { editStart = true },
                onSocInputChange = {},
                onSocConfirm = {},
                onSocDismiss = {},
                onEditArrivalSoc = { editArrival = true },
                onArrivalSocInputChange = {},
                onArrivalSocConfirm = {},
                onArrivalSocDismiss = {},
            ),
        )
    }

    @Test
    fun `the start level, the reason and the destination show, and nothing to send`() {
        content()

        compose.onNodeWithText(compose.string(Res.string.trip_dep_now, 5)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.plan_failed_no_charger_at_start)).assertIsDisplayed()
        compose.onNodeWithText(Fixtures.plan.destination.name).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.trip_send_maps)).assertDoesNotExist()
    }

    @Test
    fun `stuck further along, the reason says where`() {
        content(UnreachableTrip.NoCharger(afterKm = 120.0))

        compose.onNodeWithText(compose.string(Res.string.plan_failed_no_charger, 120)).assertIsDisplayed()
    }

    @Test
    fun `without a route the reason says so`() {
        content(UnreachableTrip.NoRoute)

        compose.onNodeWithText(compose.string(Res.string.plan_failed_no_route)).assertIsDisplayed()
    }

    @Test
    fun `without the server the reason says so`() {
        content(UnreachableTrip.NoConnection)

        compose.onNodeWithText(compose.string(Res.string.plan_failed_no_connection)).assertIsDisplayed()
    }

    @Test
    fun `start and destination open their level editors`() {
        content()

        compose.onNodeWithText(compose.string(Res.string.trip_dep_now, 5)).performClick()
        compose.onNodeWithText(Fixtures.plan.destination.name).performClick()

        assertTrue(editStart)
        assertTrue(editArrival)
    }
}
