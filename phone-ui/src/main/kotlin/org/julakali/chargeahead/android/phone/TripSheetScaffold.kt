package org.julakali.chargeahead.android.phone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import org.julakali.chargeahead.android.phone.components.AppSnackbarHost
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import org.julakali.chargeahead.android.phone.theme.ChargeAheadMotion
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.shared.domain.PlannedStop
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
    /** How far the map's floating buttons reach up from the bottom edge; 0 when none are shown. */
    floatingControlsHeight: Dp,
    viewModel: TripViewModel = koinViewModel(),
    content: @Composable (peek: Dp) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val shown = trip != null
    // Without stops there is nothing to expand to.
    val expandable = layout == TripListLayout.LIST && trip is TripUiState.Planned

    LaunchedEffect(expandable) {
        if (!expandable) sheetState.partialExpand()
    }

    // The scaffold snaps to a new peek; animating the value makes a trip glide in and out.
    val peek by animateDpAsState(
        targetValue = if (shown) tripPeekHeight() else 0.dp,
        animationSpec = ChargeAheadMotion.surface(),
        label = "sheet peek",
    )

    BoxWithConstraints {
        val layoutHeightPx = constraints.maxHeight
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peek,
            sheetSwipeEnabled = expandable,
            // The handle lives inside the content so the content's height is the whole visible sheet.
            sheetDragHandle = null,
            sheetContainerColor = MaterialTheme.colorScheme.background,
            // Material puts it on top of the sheet; without one it stays clear of the buttons and the gesture bar.
            snackbarHost = {
                val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottom = when {
                    !shown -> maxOf(floatingControlsHeight, navigationBar)
                    // Material puts it at the screen's bottom edge then.
                    sheetState.currentValue == SheetValue.Expanded -> navigationBar
                    else -> 0.dp
                }
                AppSnackbarHost(snackbar, Modifier.padding(bottom = bottom))
            },
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
                            enter = expandVertically(ChargeAheadMotion.spatial()) + fadeIn(ChargeAheadMotion.effects()),
                            exit = shrinkVertically(ChargeAheadMotion.spatial()) + fadeOut(ChargeAheadMotion.effects()),
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
                            layout = layout,
                            selection = trip.selection,
                            onToggleSelecting = viewModel::onSectionSelectingToggled,
                            onPickPoint = viewModel::onSectionPointPicked,
                            onSectionSent = viewModel::onSectionSent,
                            onOpenStop = onOpenStop,
                            onSendToMaps = { onSendToMaps(trip.mapsUrl) },
                            socEditing = SocEditing(
                                socInput = trip.socInput,
                                arrivalSocInput = trip.arrivalSocInput,
                                askedForReplan = trip.socAskedForReplan,
                                onEditStartSoc = viewModel::onStartSocEditRequested,
                                onSocInputChange = viewModel::onStartSocInputChanged,
                                onSocConfirm = viewModel::onStartSocConfirmed,
                                onSocDismiss = viewModel::onStartSocEditDismissed,
                                onEditArrivalSoc = viewModel::onArrivalSocEditRequested,
                                onArrivalSocInputChange = viewModel::onArrivalSocInputChanged,
                                onArrivalSocConfirm = viewModel::onArrivalSocConfirmed,
                                onArrivalSocDismiss = viewModel::onArrivalSocEditDismissed,
                            ),
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

