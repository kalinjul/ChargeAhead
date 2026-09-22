package org.julakali.chargeahead.uitests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.LocalNow
import org.julakali.chargeahead.android.phone.theme.ChargeAheadTheme
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.TripPlan
import java.time.LocalTime

/** The clock every golden reads; wall time must never leak into a reference image. */
val FixedNow: LocalTime = LocalTime.of(14, 30)

val Hamburg = LatLon(53.5511, 9.9937)
val Munich = LatLon(48.1351, 11.5820)

private fun site(id: String, name: String, operator: String, kw: Double, lat: Double, lon: Double, street: String, town: String) =
    ChargeSite(
        id = id,
        name = name,
        operator = operator,
        position = LatLon(lat, lon),
        connectors = listOf(Connector(ConnectorType.CCS2, kw, 4)),
        address = Address(street = street, town = town),
    )

val IonitySite = site("ocm:1", "Ionity Hildesheimer Börde", "Ionity", 350.0, 52.10, 9.95, "Rasthof Hildesheimer Börde West", "31185 Söhlde")
val EnbwSite = site("ocm:2", "EnBW Ladepark Kassel", "EnBW", 300.0, 51.30, 9.45, "Am Rasthof 3", "34266 Niestetal")
val AralSite = site("ocm:3", "Aral pulse Nürnberg-Feucht", "Aral pulse", 300.0, 49.38, 11.20, "Autobahnraststätte Nürnberg-Feucht Ost", "90537 Feucht")

/** Hamburg → München, three stops. */
val SamplePlan = TripPlan(
    route = Route(points = listOf(Hamburg, Munich), distanceKm = 776.0, durationMinutes = 452.0),
    destination = Destination(name = "München", position = Munich, address = "Marienplatz 1, 80331 München"),
    stops = listOf(
        PlannedStop(
            site = IonitySite,
            kmFromStart = 168.0,
            arrivalSocPercent = 24.0,
            departureSocPercent = 78.0,
            chargeKwh = 41.0,
            chargeMinutes = 22.0,
            etaMinutesFromStart = 98.0 + 22.0 + 5.0,
            maxPowerKw = 350.0,
            stopMinutes = 5.0,
        ),
        PlannedStop(
            site = EnbwSite,
            kmFromStart = 398.0,
            arrivalSocPercent = 18.0,
            departureSocPercent = 80.0,
            chargeKwh = 47.0,
            chargeMinutes = 27.0,
            etaMinutesFromStart = 260.0 + 27.0 + 5.0,
            maxPowerKw = 300.0,
            stopMinutes = 5.0,
        ),
        PlannedStop(
            site = AralSite,
            kmFromStart = 612.0,
            arrivalSocPercent = 21.0,
            departureSocPercent = 65.0,
            chargeKwh = 33.0,
            chargeMinutes = 18.0,
            etaMinutesFromStart = 403.0 + 18.0 + 5.0,
            maxPowerKw = 300.0,
            stopMinutes = 5.0,
        ),
    ),
    driveMinutes = 452.0,
    chargeMinutes = 67.0,
    arrivalSocPercent = 15.0,
    stopMinutes = 15.0,
)

/** App theme plus a pinned clock; every preview goes through here. */
@Composable
fun PreviewScaffold(content: @Composable () -> Unit) {
    ChargeAheadTheme {
        CompositionLocalProvider(LocalNow provides { FixedNow }) {
            content()
        }
    }
}

/** A sheet-sized box: the trip content needs a bounded height for its lazy lists. */
@Composable
fun SheetBox(content: @Composable () -> Unit) {
    PreviewScaffold {
        Box(Modifier.width(400.dp).height(320.dp)) { content() }
    }
}
