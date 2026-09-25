package org.julakali.chargeahead.android.phone

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.ui.TripListLayout
import org.julakali.chargeahead.shared.ui.TripUiState
import org.julakali.chargeahead.shared.ui.TripViewModel
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The map with the trip sheet under it. The sheet only peeks while [trip] is
 * planned; [content] gets that peek so the map can keep the route above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSheetScaffold(
    trip: TripUiState.Planned?,
    layout: TripListLayout,
    sheetState: SheetState,
    snackbar: SnackbarHostState,
    onLayoutChanged: (TripListLayout) -> Unit,
    onReplan: () -> Unit,
    onOpenStop: (PlannedStop) -> Unit,
    onSendToMaps: (String) -> Unit,
    viewModel: TripViewModel = koinViewModel(),
    content: @Composable (peek: Dp) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val expandable = layout == TripListLayout.LIST

    LaunchedEffect(expandable) {
        if (!expandable) sheetState.partialExpand()
    }

    // The scaffold snaps to a new peek; animating the value makes a trip glide in and out.
    val peek by animateDpAsState(
        targetValue = if (trip != null) tripPeekHeight() else 0.dp,
        animationSpec = tween(260),
        label = "sheet peek",
    )

    BoxWithConstraints {
        val layoutHeightPx = constraints.maxHeight
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peek,
            sheetSwipeEnabled = trip != null && expandable,
            // The handle lives inside the content so the content's height is the whole visible sheet.
            sheetDragHandle = null,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
            sheetContent = {
                if (trip == null) return@BottomSheetScaffold
                // The sheet's expanded position comes from this content's height, so the
                // height stays constant; only the inner column follows the visible part.
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
                    // Everything visible lives in this column; the stops take what the
                    // summary leaves, resolved in the same layout pass (no measured lag).
                    Column(Modifier.fillMaxWidth().visibleSheetHeight(sheetState, layoutHeightPx, peek)) {
                        // The handle is the "you can expand this" hint; it folds away with the slide.
                        AnimatedVisibility(
                            visible = expandable,
                            enter = expandVertically(tween(LAYOUT_SLIDE_MILLIS)) + fadeIn(tween(LAYOUT_SLIDE_MILLIS)),
                            exit = shrinkVertically(tween(LAYOUT_SLIDE_MILLIS)) + fadeOut(tween(LAYOUT_SLIDE_MILLIS)),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            BottomSheetDefaults.DragHandle()
                        }
                        TripSummary(
                            trip.plan,
                            layout = layout,
                            onToggleLayout = {
                                if (expandable) {
                                    // Tiles only exist collapsed: come down first, then slide.
                                    scope.launch {
                                        sheetState.partialExpand()
                                        onLayoutChanged(TripListLayout.TILES)
                                    }
                                } else {
                                    onLayoutChanged(TripListLayout.LIST)
                                }
                            },
                            onReplan = onReplan,
                        )
                        TripSheetContent(
                            plan = trip.plan,
                            startSocPercent = trip.startSocPercent,
                            isSaved = trip.isSaved,
                            layout = layout,
                            selection = trip.selection,
                            socInput = trip.socInput,
                            arrivalSocInput = trip.arrivalSocInput,
                            onToggleSelecting = viewModel::onSectionSelectingToggled,
                            onPickPoint = viewModel::onSectionPointPicked,
                            onSectionSent = viewModel::onSectionSent,
                            onOpenStop = onOpenStop,
                            onSendToMaps = { onSendToMaps(trip.mapsUrl) },
                            onToggleSave = { viewModel.toggleSaved(trip.plan.summaryLine(context)) },
                            onEditStartSoc = viewModel::onStartSocEditRequested,
                            onSocInputChange = viewModel::onStartSocInputChanged,
                            onSocConfirm = viewModel::onStartSocConfirmed,
                            onSocDismiss = viewModel::onStartSocEditDismissed,
                            onEditArrivalSoc = viewModel::onArrivalSocEditRequested,
                            onArrivalSocInputChange = viewModel::onArrivalSocInputChanged,
                            onArrivalSocConfirm = viewModel::onArrivalSocConfirmed,
                            onArrivalSocDismiss = viewModel::onArrivalSocEditDismissed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
        ) { _ ->
            content(peek)
        }
    }
}

/**
 * Fixes the height to the part of the sheet that is on screen, at least [peek].
 * The sheet offset changes every frame while dragging, so it is read in the
 * layout phase; it has no value until the scaffold has placed its anchors,
 * which happens after this column is first measured.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.visibleSheetHeight(sheetState: SheetState, layoutHeightPx: Int, peek: Dp) =
    layout { measurable, constraints ->
        val peekPx = peek.roundToPx()
        val visible = if (sheetState.hasPartiallyExpandedState) {
            (layoutHeightPx - sheetState.requireOffset()).roundToInt().coerceAtLeast(peekPx)
        } else {
            peekPx
        }
        val height = constraints.constrainHeight(visible)
        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/** Shown under a saved route's name. */
private fun TripPlan.summaryLine(context: Context): String =
    context.getString(R.string.trip_summary_distance, route.distanceKm.roundToInt()) + " · " +
        context.resources.getQuantityString(R.plurals.trip_summary_stops, stops.size, stops.size)
