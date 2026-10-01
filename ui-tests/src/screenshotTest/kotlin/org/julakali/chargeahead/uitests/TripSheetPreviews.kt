package org.julakali.chargeahead.uitests

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.julakali.chargeahead.android.phone.DestinationHeader
import org.julakali.chargeahead.shared.ui.TripListLayout
import org.julakali.chargeahead.android.phone.SocEditing
import org.julakali.chargeahead.android.phone.TripSheetContent
import org.julakali.chargeahead.android.phone.TripSummary
import org.julakali.chargeahead.android.phone.headerLine
import org.julakali.chargeahead.shared.domain.SectionSelection

@Composable
private fun Sheet(layout: TripListLayout, selection: SectionSelection = SectionSelection()) {
    SheetBox {
        TripSheetContent(
            plan = SamplePlan,
            startSocPercent = 80.0,
            layout = layout,
            selection = selection,
            onToggleSelecting = {},
            onPickPoint = {},
            onSectionSent = {},
            onOpenStop = {},
            onSendToMaps = {},
            socEditing = SocEditing(null, null, false, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun TripSheetList() = Sheet(TripListLayout.LIST)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun TripSheetListSectionPicked() = Sheet(TripListLayout.LIST, SectionSelection(selecting = true, a = 1, b = 2))

@PreviewTest
@Preview(showBackground = true)
@Composable
fun TripSheetTiles() = Sheet(TripListLayout.TILES)

@PreviewTest
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
fun TripSheetListLargeFont() = Sheet(TripListLayout.LIST)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun TripSummaryList() {
    PreviewScaffold {
        Box(Modifier.width(400.dp)) {
            TripSummary(plan = SamplePlan, layout = TripListLayout.LIST, onToggleLayout = {}, onReplan = {})
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun TripSummaryTiles() {
    PreviewScaffold {
        Box(Modifier.width(400.dp)) {
            TripSummary(plan = SamplePlan, layout = TripListLayout.TILES, onToggleLayout = {}, onReplan = {})
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun DestinationHeaderWithPlan() {
    PreviewScaffold {
        Box(Modifier.width(400.dp).padding(8.dp)) {
            DestinationHeader(title = SamplePlan.destination.name, subtitle = SamplePlan.headerLine(), onClear = {}, onTitleClick = {})
        }
    }
}

// Dark theme: the same components under UI_MODE_NIGHT_YES.
@PreviewTest
@Preview(showBackground = true, backgroundColor = 0xFF121212, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TripSheetListDark() = Sheet(TripListLayout.LIST)

@PreviewTest
@Preview(showBackground = true, backgroundColor = 0xFF121212, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TripSheetListSectionPickedDark() = Sheet(TripListLayout.LIST, SectionSelection(selecting = true, a = 1, b = 2))

@PreviewTest
@Preview(showBackground = true, backgroundColor = 0xFF121212, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TripSheetTilesDark() = Sheet(TripListLayout.TILES)

@PreviewTest
@Preview(showBackground = true, backgroundColor = 0xFF121212, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DestinationHeaderWithPlanDark() = DestinationHeaderWithPlan()

/** A phone's header width with a long trip line: the subtitle steps down instead of being cut. */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun DestinationHeaderNarrowLongTrip() {
    PreviewScaffold {
        Box(Modifier.width(260.dp).padding(8.dp)) {
            DestinationHeader(title = "Kiel", subtitle = "873 km · 11h 16m", onClear = {}, onTitleClick = {})
        }
    }
}
