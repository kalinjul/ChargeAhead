package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.julakali.chargeahead.android.phone.ChargeStopDetailSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChargeStopDetailSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun sheet(tripLine: String? = null) = compose.setThemedContent {
        ChargeStopDetailSheet(stop = Fixtures.chargeStop, live = null, onDismiss = {}, tripLine = tripLine)
    }

    @Test
    fun `operator and address head the sheet`() {
        sheet()
        compose.onNodeWithText("Ionity").assertIsDisplayed()
        compose.onNodeWithText("Autobahnstraße 1, 12345 Hannover").assertIsDisplayed()
    }

    @Test
    fun `the trip line shows when given`() {
        sheet(tripLine = "Ankunft 16:00 · Abfahrt 16:30")
        compose.onNodeWithText("Ankunft 16:00 · Abfahrt 16:30").assertIsDisplayed()
    }

    @Test
    fun `no trip line outside a trip`() {
        sheet()
        compose.onNodeWithText("Abfahrt", substring = true).assertDoesNotExist()
    }
}
