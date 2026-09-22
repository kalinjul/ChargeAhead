package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.julakali.chargeahead.android.phone.NetworkSettingsScreen
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.ui.NetworksUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkSettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `tapping a network pill marks it as picked`() {
        val networks = listOf(
            Network(key = "ionity", name = "Ionity"),
            Network(key = "enbw", name = "EnBW"),
            Network(key = "fastned", name = "Fastned"),
        )
        // The ui state arrives from the ViewModel as one value, as NetworksRoute passes it,
        // so a toggle recomposes the screen without changing any pill's key.
        var uiState by mutableStateOf(NetworksUiState(networks = networks))

        compose.setThemedContent {
            NetworkSettingsScreen(
                uiState = uiState,
                onSearchChange = {},
                onNetworkToggled = { key -> uiState = uiState.copy(selected = uiState.selected + key) },
                onOnlyPreferredChange = {},
            )
        }
        compose.onNodeWithText("Ionity").assertIsNotSelected()

        compose.onNodeWithText("Ionity").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Ionity").assertIsSelected()
        compose.onNodeWithText("EnBW").assertIsNotSelected()
    }
}
