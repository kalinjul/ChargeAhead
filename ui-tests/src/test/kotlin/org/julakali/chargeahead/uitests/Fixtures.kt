package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.julakali.chargeahead.android.phone.LocalNow
import org.julakali.chargeahead.android.phone.theme.ChargeAheadTheme
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.Reachability
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.ui.SearchRow
import java.time.LocalTime

/** Hamburg → München with three stops; the numbers are round so the rows read predictably. */
object Fixtures {
    val hamburg = LatLon(53.5511, 9.9937)
    val muenchen = LatLon(48.1351, 11.5820)

    /** The clock every trip row reads in tests. */
    val now: LocalTime = LocalTime.of(14, 30)

    val destination = Destination("München", muenchen, "Marienplatz 1, 80331 München")

    fun site(id: String, operator: String, position: LatLon, town: String) = ChargeSite(
        id = id,
        name = "$operator $town",
        operator = operator,
        position = position,
        connectors = listOf(Connector(ConnectorType.CCS2, 300.0, 4)),
        address = Address(street = "Autobahnstraße 1", postalCode = "12345", town = town),
        sources = setOf("ocm"),
    )

    val stopHannover = PlannedStop(
        site = site("ocm:1", "Ionity", LatLon(52.3759, 9.7320), "Hannover"),
        kmFromStart = 150.0,
        arrivalSocPercent = 10.0,
        departureSocPercent = 80.0,
        chargeKwh = 50.0,
        chargeMinutes = 30.0,
        etaMinutesFromStart = 120.0,
        maxPowerKw = 300.0,
    )
    val stopKassel = PlannedStop(
        site = site("ocm:2", "EnBW", LatLon(51.3127, 9.4797), "Kassel"),
        kmFromStart = 320.0,
        arrivalSocPercent = 15.0,
        departureSocPercent = 80.0,
        chargeKwh = 45.0,
        chargeMinutes = 25.0,
        etaMinutesFromStart = 270.0,
        maxPowerKw = 150.0,
    )
    val stopNuernberg = PlannedStop(
        site = site("ocm:3", "Aral pulse", LatLon(49.4521, 11.0767), "Nürnberg"),
        kmFromStart = 620.0,
        arrivalSocPercent = 12.0,
        departureSocPercent = 60.0,
        chargeKwh = 35.0,
        chargeMinutes = 20.0,
        etaMinutesFromStart = 480.0,
        maxPowerKw = 350.0,
    )

    val plan = TripPlan(
        route = Route(
            points = listOf(hamburg, stopHannover.site.position, stopKassel.site.position, stopNuernberg.site.position, muenchen),
            distanceKm = 790.0,
            durationMinutes = 465.0,
        ),
        destination = destination,
        stops = listOf(stopHannover, stopKassel, stopNuernberg),
        driveMinutes = 465.0,
        chargeMinutes = 75.0,
        arrivalSocPercent = 20.0,
    )

    val chargeStop = ChargeStop(
        site = stopHannover.site,
        distanceKm = 3.2,
        reachability = Reachability.REACHABLE,
        socOnArrivalPercent = 45.0,
    )

    fun searchRow(title: String, detail: String? = null, distanceKm: Double? = null, recent: Boolean = true) =
        SearchRow(Destination(title, muenchen, detail), title, detail, distanceKm, recent)
}

typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** Every test renders inside the theme with the clock pinned. */
fun ComposeRule.setThemedContent(content: @Composable () -> Unit) {
    setContent {
        CompositionLocalProvider(LocalNow provides { Fixtures.now }) {
            ChargeAheadTheme(content)
        }
    }
}

fun ComposeRule.string(id: Int, vararg args: Any): String = activity.getString(id, *args)
