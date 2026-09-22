package org.julakali.chargeahead.uitests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.julakali.chargeahead.android.phone.ChargerDot
import org.julakali.chargeahead.android.phone.ChargerPill
import org.julakali.chargeahead.shared.domain.ChargeSpeed
import org.julakali.chargeahead.shared.domain.SiteAvailability

private val LiveGood = SiteAvailability.Live(free = 6, total = 8)
private val LiveLow = SiteAvailability.Live(free = 1, total = 4)
private val LiveNone = SiteAvailability.Live(free = 0, total = 2)

@Composable
private fun Pill(speed: ChargeSpeed, label: String?, availability: SiteAvailability?) {
    PreviewScaffold {
        ChargerPill(speed = speed, label = label, availability = availability, modifier = Modifier.padding(8.dp))
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ChargerPillOneBoltNoLive() = Pill(ChargeSpeed.FAST, "EnBW", null)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ChargerPillTwoBoltsNoLive() = Pill(ChargeSpeed.ULTRA, "Tesla", null)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ChargerPillThreeBoltsNoLive() = Pill(ChargeSpeed.HYPER, "Ionity", null)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ChargerPillLiveGood() = Pill(ChargeSpeed.HYPER, "Ionity", LiveGood)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ChargerPillLiveLow() = Pill(ChargeSpeed.ULTRA, "Aral pulse", LiveLow)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ChargerPillLiveNone() = Pill(ChargeSpeed.MEDIUM, "EnBW", LiveNone)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ChargerPillOutOfOrder() = Pill(ChargeSpeed.FAST, "EWE Go", SiteAvailability.OutOfOrder)

@PreviewTest
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
fun ChargerPillLiveGoodLargeFont() = Pill(ChargeSpeed.HYPER, "Ionity", LiveGood)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ChargerDotStates() {
    PreviewScaffold {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(8.dp)) {
            ChargerDot(speed = ChargeSpeed.SLOW, availability = null)
            ChargerDot(speed = ChargeSpeed.MEDIUM, availability = null)
            ChargerDot(speed = ChargeSpeed.HYPER, availability = null)
            ChargerDot(speed = ChargeSpeed.FAST, availability = LiveGood)
            ChargerDot(speed = ChargeSpeed.FAST, availability = LiveLow)
            ChargerDot(speed = ChargeSpeed.FAST, availability = LiveNone)
            ChargerDot(speed = ChargeSpeed.FAST, availability = SiteAvailability.OutOfOrder)
        }
    }
}
