package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.HomeMode
import org.julakali.chargeahead.android.phone.HomeScreen
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.ui.HomeUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun home(
        mode: HomeMode = HomeMode.BROWSING,
        uiState: HomeUiState = HomeUiState(),
        onSettings: () -> Unit = {},
        activeRouteEnabled: Boolean = false,
        onActiveRoute: () -> Unit = {},
    ) = compose.setThemedContent {
        HomeScreen(
            uiState = uiState,
            hasPermission = true,
            planningInProgress = false,
            mode = mode,
            route = null,
            mapBottomInset = 0.dp,
            onViewportChanged = {},
            onChargerTapped = {},
            onRequestPermission = {},
            onLocate = {},
            onSettings = onSettings,
            onChargeNow = {},
            activeRouteEnabled = activeRouteEnabled,
            onActiveRoute = onActiveRoute,
            onDismissSearch = {},
            onStopTapped = {},
            topBar = { Text("top bar") },
        )
    }

    @Test
    fun `browsing shows both pills`() {
        home()
        compose.onNodeWithText(compose.string(R.string.home_pill_charge_now)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(R.string.home_pill_active_route)).assertIsDisplayed()
    }

    @Test
    fun `aktive route is dimmed without a committed trip`() {
        home(activeRouteEnabled = false)
        compose.onNodeWithText(compose.string(R.string.home_pill_active_route)).assertIsNotEnabled()
    }

    @Test
    fun `aktive route opens the committed trip`() {
        var opened = false
        home(activeRouteEnabled = true, onActiveRoute = { opened = true })
        compose.onNodeWithText(compose.string(R.string.home_pill_active_route)).assertIsEnabled().performClick()
        assertTrue(opened)
    }

    @Test
    fun `zoomed out too far shows a clickable hint`() {
        home(uiState = HomeUiState(belowMinZoom = true))
        compose.onNodeWithText(compose.string(R.string.map_zoom_hint)).assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun `a trip hides the pills`() {
        home(mode = HomeMode.TRIP)
        compose.onNodeWithText(compose.string(R.string.home_pill_charge_now)).assertDoesNotExist()
        compose.onNodeWithText(compose.string(R.string.home_pill_active_route)).assertDoesNotExist()
    }

    @Test
    fun `the settings button opens settings`() {
        var opened = false
        home(onSettings = { opened = true })
        compose.onNodeWithContentDescription(compose.string(R.string.home_settings)).performClick()
        assertTrue(opened)
    }
}
