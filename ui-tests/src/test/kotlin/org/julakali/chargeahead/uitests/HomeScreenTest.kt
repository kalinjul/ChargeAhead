package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
            onRoutes = {},
            onDismissSearch = {},
            onStopTapped = {},
            topBar = { Text("top bar") },
            topPanel = { Text("top panel") },
        )
    }

    @Test
    fun `browsing shows both pills`() {
        home()
        compose.onNodeWithText(compose.string(R.string.home_pill_charge_now)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(R.string.home_pill_favorites)).assertIsDisplayed()
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
        compose.onNodeWithText(compose.string(R.string.home_pill_favorites)).assertDoesNotExist()
    }

    @Test
    fun `searching shows the top panel`() {
        home(mode = HomeMode.SEARCHING)
        compose.onNodeWithText("top panel").assertIsDisplayed()
    }

    @Test
    fun `the settings button opens settings`() {
        var opened = false
        home(onSettings = { opened = true })
        compose.onNodeWithContentDescription(compose.string(R.string.home_settings)).performClick()
        assertTrue(opened)
    }
}
