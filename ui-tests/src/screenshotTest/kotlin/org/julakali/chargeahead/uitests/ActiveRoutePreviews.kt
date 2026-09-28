package org.julakali.chargeahead.uitests

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.julakali.chargeahead.android.phone.ActiveRouteScreen
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.ui.SectionSelection

@Composable
private fun Screen(selection: SectionSelection = SectionSelection(), planning: Boolean = false) {
    PreviewScaffold {
        Box(Modifier.width(400.dp).height(560.dp)) {
            ActiveRouteScreen(
                trip = CommittedTrip(SamplePlan, startSocPercent = 80.0, committedAtEpochMillis = 0L),
                selection = selection,
                planning = planning,
                onToggleSelecting = {},
                onPickPoint = {},
                onSectionSent = {},
                onOpenStop = {},
                onSendToMaps = {},
                onReplan = {},
                onEnd = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ActiveRoute() = Screen()

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ActiveRouteSectionPicked() = Screen(SectionSelection(selecting = true, a = 1, b = 2))

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ActiveRouteReplanning() = Screen(planning = true)

@PreviewTest
@Preview(showBackground = true, backgroundColor = 0xFF121212, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ActiveRouteDark() = Screen()
