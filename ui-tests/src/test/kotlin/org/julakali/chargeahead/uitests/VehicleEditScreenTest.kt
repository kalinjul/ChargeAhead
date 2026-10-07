package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.julakali.chargeahead.android.phone.VehicleEditActions
import org.julakali.chargeahead.android.phone.VehicleEditScreen
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.ui.VehicleEditUiState
import org.julakali.chargeahead.shared.ui.VehicleEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.garage_kw
import org.julakali.chargeahead.shared.resources.garage_kwh
import org.julakali.chargeahead.shared.resources.garage_kwh_per_100
import org.julakali.chargeahead.shared.resources.phone_action_restore_catalog_values
import org.julakali.chargeahead.shared.resources.soc_dialog_apply
import org.julakali.chargeahead.shared.resources.soc_dialog_cancel
import org.julakali.chargeahead.shared.resources.vehicle_catalog_value
import org.julakali.chargeahead.shared.resources.vehicle_consumption
import org.julakali.chargeahead.shared.resources.vehicle_model_own
import org.julakali.chargeahead.shared.resources.vehicle_number_invalid
import org.julakali.chargeahead.shared.resources.vehicle_remove
import org.julakali.chargeahead.shared.resources.vehicle_remove_confirm
import org.julakali.chargeahead.shared.resources.vehicle_remove_title
import org.julakali.chargeahead.shared.resources.vehicle_restore_unavailable

@RunWith(RobolectricTestRunner::class)
class VehicleEditScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val id3 = VehicleProfile("VW ID.3 Pro S", 77.0, 16.5, setOf(ConnectorType.CCS2), dcPeakPowerKw = 175.0, modelId = "id3")

    private val catalogId3 = VehiclePreset("id3", "VW ID.3 Pro S (2023)", 77.0, 15.9, 175.0, setOf(ConnectorType.CCS2))

    private var openedConsumption = false
    private var restored = false
    private var removed = false
    private var removeConfirmed = false
    private var dismissed = false

    private fun page(state: VehicleEditUiState) = compose.setThemedContent {
        VehicleEditScreen(
            uiState = state,
            actions = VehicleEditActions(
                onConsumptionOpen = { openedConsumption = true },
                onRestoreCatalogValues = { restored = true },
                onRemove = { removed = true },
                onDismiss = { dismissed = true },
                onRemoveConfirm = { removeConfirmed = true },
            ),
        )
    }

    @Test
    fun `the catalog model heads the page, every value shows on its row`() {
        page(VehicleEditUiState(vehicle = id3.copy(displayName = "Familienkutsche"), catalog = catalogId3))

        compose.onNodeWithText(catalogId3.name).assertIsDisplayed()
        compose.onNodeWithText("Familienkutsche").assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.garage_kwh_per_100, "16,5")).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.garage_kwh, "77")).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.garage_kw, 175)).assertIsDisplayed()
    }

    @Test
    fun `a car typed in by hand says so where the model would be`() {
        page(VehicleEditUiState(vehicle = id3.copy(modelId = null)))

        compose.onNodeWithText(compose.string(Res.string.vehicle_model_own)).assertIsDisplayed()
    }

    @Test
    fun `a value's dialog names the catalog's value`() {
        page(VehicleEditUiState(vehicle = id3, catalog = catalogId3, editor = VehicleEditor.Battery("70")))

        compose.onNodeWithText(compose.string(Res.string.vehicle_catalog_value, compose.string(Res.string.garage_kwh, "77"))).assertIsDisplayed()
    }

    @Test
    fun `a row opens its editor`() {
        page(VehicleEditUiState(vehicle = id3))

        compose.onNodeWithText(compose.string(Res.string.vehicle_consumption)).performClick()

        assertTrue(openedConsumption)
    }

    @Test
    fun `the consumption is a typed field with the catalog's value under it`() {
        page(VehicleEditUiState(vehicle = id3, catalog = catalogId3, editor = VehicleEditor.Consumption("16,5")))

        compose.onNodeWithText(
            compose.string(Res.string.vehicle_catalog_value, compose.string(Res.string.garage_kwh_per_100, "15,9")),
        ).assertIsDisplayed()
    }

    @Test
    fun `a number that does not parse says so and cannot be applied`() {
        page(VehicleEditUiState(vehicle = id3, editor = VehicleEditor.Battery("viel")))

        compose.onNodeWithText(compose.string(Res.string.vehicle_number_invalid)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.soc_dialog_apply)).assertIsNotEnabled()
    }

    /** Typed values open a dialog, which sits above the keyboard instead of riding up on it. */
    @Test
    fun `the name opens in a dialog that can be cancelled`() {
        page(VehicleEditUiState(vehicle = id3, editor = VehicleEditor.Name(id3.displayName)))

        compose.onNodeWithText(compose.string(Res.string.soc_dialog_cancel)).performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `catalog values come back for a changed catalog car`() {
        page(VehicleEditUiState(vehicle = id3, catalog = catalogId3, canRestoreCatalogValues = true))

        compose.onNodeWithText(compose.string(Res.string.phone_action_restore_catalog_values)).performClick()

        assertTrue(restored)
    }

    @Test
    fun `an own car shows the catalog row disabled, saying why`() {
        page(VehicleEditUiState(vehicle = id3.copy(modelId = null)))

        compose.onNodeWithText(compose.string(Res.string.vehicle_restore_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.phone_action_restore_catalog_values)).performClick()

        assertFalse(restored)
    }

    @Test
    fun `the car is removed from its own page`() {
        page(VehicleEditUiState(vehicle = id3))

        compose.onNodeWithText(compose.string(Res.string.vehicle_remove)).performClick()

        assertTrue(removed)
    }

    @Test
    fun `removal asks before it removes`() {
        page(VehicleEditUiState(vehicle = id3, confirmingRemoval = true))

        compose.onNodeWithText(compose.string(Res.string.vehicle_remove_title, id3.displayName)).assertIsDisplayed()
        compose.onNodeWithText(compose.string(Res.string.vehicle_remove_confirm)).performClick()

        assertTrue(removeConfirmed)
    }
}
