package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getBoundsInRoot
import org.julakali.chargeahead.android.phone.AddCarScreen
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.ui.AddCarUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.addcar_custom
import org.julakali.chargeahead.shared.resources.addcar_custom_default_name
import org.julakali.chargeahead.shared.resources.addcar_custom_named

@RunWith(RobolectricTestRunner::class)
class AddCarScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ioniq = VehiclePreset("ioniq5", "Hyundai Ioniq 5", 74.0, 17.9, 230.0, setOf(ConnectorType.CCS2))
    private var created: String? = null

    private fun search(state: AddCarUiState) = compose.setThemedContent {
        AddCarScreen(uiState = state, onSearchChange = {}, onAdd = {}, onCreateCustom = { created = it })
    }

    @Test
    fun `an own car comes after the catalog hits`() {
        search(AddCarUiState(query = "Hyun", matches = listOf(ioniq)))

        val hit = compose.onNodeWithText(ioniq.name).getBoundsInRoot()
        val own = compose.onNodeWithText(compose.string(Res.string.addcar_custom_named, "Hyun")).getBoundsInRoot()
        assertTrue(own.top > hit.top)
    }

    @Test
    fun `without a hit the own car is all there is, under the searched name`() {
        search(AddCarUiState(query = " Fiat 500e "))

        assertEquals(1, compose.onAllNodesWithText(compose.string(Res.string.addcar_custom_named, "Fiat 500e")).fetchSemanticsNodes().size)
        compose.onNodeWithText(compose.string(Res.string.addcar_custom_named, "Fiat 500e")).performClick()

        assertEquals("Fiat 500e", created)
    }

    @Test
    fun `an empty search offers an own car under a default name`() {
        search(AddCarUiState())

        compose.onNodeWithText(compose.string(Res.string.addcar_custom)).performClick()

        assertEquals(compose.string(Res.string.addcar_custom_default_name), created)
    }
}
