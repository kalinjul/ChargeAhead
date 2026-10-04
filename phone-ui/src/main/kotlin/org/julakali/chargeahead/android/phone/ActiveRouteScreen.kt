package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import org.julakali.chargeahead.android.phone.components.AppSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.components.SocEditDialog
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.ui.CommittedTripEvent
import org.julakali.chargeahead.shared.ui.CommittedTripViewModel
import org.julakali.chargeahead.shared.domain.SectionSelection
import org.julakali.chargeahead.shared.ui.TripListLayout
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/** The committed trip's page. Leaves via [onEnded] once the trip is gone. */
@Composable
fun ActiveRouteRoute(
    onOpenStop: (PlannedStop) -> Unit,
    onSendToMaps: (String) -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommittedTripViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(event) {
        when (event) {
            null -> return@LaunchedEffect
            CommittedTripEvent.Replanned -> snackbar.showSnackbar(context.getString(R.string.active_route_replanned))
            CommittedTripEvent.NoRoute -> snackbar.showSnackbar(context.getString(R.string.plan_failed_no_route))
            CommittedTripEvent.Ended -> onEnded()
        }
        viewModel.onEventHandled()
    }

    val trip = uiState.trip ?: return
    Box(modifier) {
        ActiveRouteScreen(
            trip = trip,
            selection = uiState.selection,
            planning = uiState.planning,
            onToggleSelecting = viewModel::onSectionSelectingToggled,
            onPickPoint = viewModel::onSectionPointPicked,
            onSectionSent = viewModel::onSectionSent,
            onOpenStop = onOpenStop,
            onSendToMaps = { uiState.mapsUrl?.let(onSendToMaps) },
            onReplan = viewModel::onReplanRequested,
            onEnd = viewModel::endTrip,
            canReplan = uiState.canReplan,
            socInput = uiState.socInput,
            onSocInputChange = viewModel::onSocInputChanged,
            onSocConfirm = viewModel::onSocConfirmed,
            onSocDismiss = viewModel::onSocEditDismissed,
            modifier = Modifier.fillMaxSize(),
        )
        AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/**
 * The stored plan as an itinerary, with the same section picking as the trip
 * sheet, and the two things one does with a trip underway: plan it anew from
 * here, or stop navigating it.
 */
@Composable
fun ActiveRouteScreen(
    trip: CommittedTrip,
    selection: SectionSelection,
    planning: Boolean,
    onToggleSelecting: () -> Unit,
    onPickPoint: (Int) -> Unit,
    onSectionSent: () -> Unit,
    onOpenStop: (PlannedStop) -> Unit,
    onSendToMaps: () -> Unit,
    onReplan: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    /** Off without a position to plan from. */
    canReplan: Boolean = true,
    /** The charge-level prompt before re-planning; `null` while closed. */
    socInput: String? = null,
    onSocInputChange: (String) -> Unit = {},
    onSocConfirm: () -> Unit = {},
    onSocDismiss: () -> Unit = {},
) {
    val plan = trip.plan
    // Without a car reading, "Neu planen" asks for the level first.
    socInput?.let { input ->
        SocEditDialog(
            value = input,
            title = stringResource(R.string.soc_dialog_title),
            confirmLabel = stringResource(R.string.trip_soc_confirm),
            onValueChange = onSocInputChange,
            onConfirm = onSocConfirm,
            onDismiss = onSocDismiss,
            supportingText = stringResource(R.string.soc_dialog_car_silent),
        )
    }
    Column(modifier) {
        // The trip on one card: totals, arrival, and what one does to the trip itself.
        AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    listOf(
                        stringResource(R.string.trip_summary_distance, plan.route.distanceKm.roundToInt()),
                        minutesText(plan.totalMinutes),
                        pluralStringResource(R.plurals.trip_summary_stops, plan.stops.size, plan.stops.size),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.trip_arr, etaText(LocalContext.current, plan.totalMinutes, LocalNow.current()), plan.arrivalSocPercent.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    // Half a card each: the labels need the room Material's default padding eats.
                    FilledTonalButton(
                        onClick = onReplan,
                        enabled = canReplan && !planning,
                        shape = MaterialTheme.shapes.small,
                        contentPadding = CARD_BUTTON_PADDING,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (planning) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(ButtonDefaults.IconSize))
                        } else {
                            Icon(painterResource(R.drawable.ic_route), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Text(stringResource(R.string.trip_replan), maxLines = 1, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    OutlinedButton(
                        onClick = onEnd,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = CARD_BUTTON_PADDING,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.active_route_end), maxLines = 1)
                    }
                }
            }
        }
        TripSheetContent(
            plan = plan,
            startSocPercent = trip.startSocPercent,
            layout = TripListLayout.LIST,
            selection = selection,
            onToggleSelecting = onToggleSelecting,
            onPickPoint = onPickPoint,
            onSectionSent = onSectionSent,
            onOpenStop = onOpenStop,
            onSendToMaps = onSendToMaps,
            socEditing = null,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Material's 24dp sides would cut "Navigieren beenden" on a phone-wide card split in two. */
private val CARD_BUTTON_PADDING = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
