package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.HomeScreen
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.ui.HomeUiState
import org.julakali.chargeahead.shared.ui.SearchUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import androidx.compose.ui.test.onNodeWithTag
import org.julakali.chargeahead.shared.domain.ChargeMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.home_pill_active_route
import org.julakali.chargeahead.shared.resources.home_pill_charge_now
import org.julakali.chargeahead.shared.resources.home_settings
import org.julakali.chargeahead.shared.resources.map_zoom_hint
import org.julakali.chargeahead.shared.resources.mode_ac
import org.julakali.chargeahead.shared.resources.mode_browse

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun home(
        trip: TripPlan? = null,
        uiState: HomeUiState = HomeUiState(),
        onSettings: () -> Unit = {},
        activeRouteEnabled: Boolean = false,
        onActiveRoute: () -> Unit = {},
        onModeDismiss: () -> Unit = {},
    ) = compose.setThemedContent {
        HomeScreen(
            uiState = uiState,
            search = SearchUiState(),
            trip = trip,
            destination = trip?.destination,
            hasPermission = true,
            planningInProgress = false,
            mapBottomInset = 0.dp,
            onViewportChanged = {},
            onChargerTapped = {},
            onRequestPermission = {},
            onLocate = {},
            onSettings = onSettings,
            onChargeNow = {},
            activeRouteEnabled = activeRouteEnabled,
            onActiveRoute = onActiveRoute,
            onModeDismiss = onModeDismiss,
            onStopTapped = {},
            onSearchExpandedChange = {},
            onQueryChange = {},
            onPick = {},
            onClearTrip = {},
        )
    }

    @Test
    fun `an active mode shows its pill and the edge glow`() {
        home(uiState = HomeUiState(mode = ChargeMode.AC))
        compose.onNodeWithText(compose.string(Res.string.mode_ac)).assertIsDisplayed()
        compose.onNodeWithTag("modeGlow").assertExists()
    }

    @Test
    fun `stoebermodus has its own pill`() {
        home(uiState = HomeUiState(mode = ChargeMode.BROWSE))
        compose.onNodeWithText(compose.string(Res.string.mode_browse)).assertIsDisplayed()
    }

    @Test
    fun `no mode means no pill and no glow`() {
        home()
        compose.onNodeWithText(compose.string(Res.string.mode_ac)).assertDoesNotExist()
        compose.onNodeWithText(compose.string(Res.string.mode_browse)).assertDoesNotExist()
        compose.onNodeWithTag("modeGlow").assertDoesNotExist()
    }

    @Test
    fun `the mode pill switches the mode off`() {
        var dismissed = false
        home(uiState = HomeUiState(mode = ChargeMode.AC), onModeDismiss = { dismissed = true })
        compose.onNodeWithText(compose.string(Res.string.mode_ac)).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `browsing shows both pills`() {
        home()
        compose.onNodeWithText(compose.string(Res.string.home_pill_charge_now)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.home_pill_active_route)).assertIsDisplayed()
    }

    @Test
    fun `aktive route is dimmed without a committed trip`() {
        home(activeRouteEnabled = false)
        compose.onNodeWithText(compose.string(Res.string.home_pill_active_route)).assertIsNotEnabled()
    }

    @Test
    fun `aktive route opens the committed trip`() {
        var opened = false
        home(activeRouteEnabled = true, onActiveRoute = { opened = true })
        compose.onNodeWithText(compose.string(Res.string.home_pill_active_route)).assertIsEnabled().performClick()
        assertTrue(opened)
    }

    @Test
    fun `zoomed out too far shows a clickable hint`() {
        home(uiState = HomeUiState(belowMinZoom = true))
        compose.onNodeWithText(compose.string(Res.string.map_zoom_hint)).assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun `a trip hides the pills`() {
        home(trip = Fixtures.plan)
        compose.onNodeWithText(compose.string(Res.string.home_pill_charge_now)).assertDoesNotExist()
        compose.onNodeWithText(compose.string(Res.string.home_pill_active_route)).assertDoesNotExist()
    }

    @Test
    fun `the settings button opens settings`() {
        var opened = false
        home(onSettings = { opened = true })
        compose.onNodeWithContentDescription(compose.string(Res.string.home_settings)).performClick()
        assertTrue(opened)
    }
}
