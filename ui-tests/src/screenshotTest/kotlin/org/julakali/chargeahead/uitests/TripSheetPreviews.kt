package org.julakali.chargeahead.uitests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.julakali.chargeahead.android.phone.DestinationHeader
import org.julakali.chargeahead.android.phone.TripListLayout
import org.julakali.chargeahead.android.phone.TripSheetContent
import org.julakali.chargeahead.android.phone.TripSummary
import org.julakali.chargeahead.android.phone.headerLine
import org.julakali.chargeahead.shared.ui.SectionSelection

@Composable
private fun Sheet(layout: TripListLayout, selection: SectionSelection = SectionSelection(), isSaved: Boolean = false) {
    SheetBox {
        TripSheetContent(
            plan = SamplePlan,
            startPosition = Hamburg,
            startSocPercent = 80.0,
            isSaved = isSaved,
            layout = layout,
            selection = selection,
            socInput = null,
            arrivalSocInput = null,
            onToggleSelecting = {},
            onPickPoint = {},
            onSectionSent = {},
            onOpenStop = {},
            onSendToMaps = {},
            onToggleSave = {},
            onEditStartSoc = {},
            onSocInputChange = {},
            onSocConfirm = {},
            onSocDismiss = {},
            onEditArrivalSoc = {},
            onArrivalSocInputChange = {},
            onArrivalSocConfirm = {},
            onArrivalSocDismiss = {},
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
fun TripSheetListSectionPicked() = Sheet(TripListLayout.LIST, SectionSelection(selecting = true, a = 1, b = 2), isSaved = true)

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
            DestinationHeader(title = SamplePlan.destination.name, subtitle = SamplePlan.headerLine(), onClear = {})
        }
    }
}
