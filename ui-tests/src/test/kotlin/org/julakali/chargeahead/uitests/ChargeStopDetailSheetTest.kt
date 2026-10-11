package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.julakali.chargeahead.android.phone.ChargeStopDetailSheet
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.PriceComponent
import org.julakali.chargeahead.shared.domain.PriceKind
import org.julakali.chargeahead.shared.domain.Tariff
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChargeStopDetailSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun sheet(tripLine: String? = null, stop: ChargeStop = Fixtures.chargeStop) = compose.setThemedContent {
        ChargeStopDetailSheet(stop = stop, live = null, onDismiss = {}, onOpenMaps = {}, tripLine = tripLine)
    }

    private val perKwh = Tariff("EUR", listOf(PriceComponent(PriceKind.PER_KWH, 0.59)))

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
    fun `the ad-hoc price shows when the site publishes one`() {
        sheet(stop = Fixtures.chargeStop.copy(site = Fixtures.chargeStop.site.copy(adHocTariffs = listOf(perKwh))))
        compose.onNodeWithText("AD-HOC-PREIS").assertIsDisplayed()
        compose.onNodeWithText("0,59 €/kWh").assertIsDisplayed()
    }

    @Test
    fun `no price section without a published price`() {
        sheet()
        compose.onNodeWithText("AD-HOC-PREIS").assertDoesNotExist()
    }

    @Test
    fun `no trip line outside a trip`() {
        sheet()
        compose.onNodeWithText("Abfahrt", substring = true).assertDoesNotExist()
    }
}
