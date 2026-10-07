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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.julakali.chargeahead.shared.resources.plan_vehicle_missing_action
import org.julakali.chargeahead.shared.resources.plan_vehicle_missing
import androidx.compose.material3.SnackbarResult
import org.julakali.chargeahead.android.phone.components.garageActionVisuals
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.components.ChargeLevelKind
import org.julakali.chargeahead.android.phone.components.ChargeLevelSheet
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.ui.CommittedTripEvent
import org.julakali.chargeahead.shared.ui.CommittedTripViewModel
import org.julakali.chargeahead.shared.domain.SectionSelection
import org.julakali.chargeahead.shared.ui.TripListLayout
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.active_route_end
import org.julakali.chargeahead.shared.resources.active_route_replanned
import org.julakali.chargeahead.shared.resources.soc_dialog_car_silent
import org.julakali.chargeahead.shared.resources.soc_dialog_title
import org.julakali.chargeahead.shared.resources.trip_arr
import org.julakali.chargeahead.shared.resources.trip_replan
import org.julakali.chargeahead.shared.resources.trip_soc_confirm
import org.julakali.chargeahead.shared.resources.trip_summary_distance
import org.julakali.chargeahead.shared.resources.trip_summary_stops

/** The committed trip's page. Leaves via [onEnded] once the trip is gone. */
@Composable
fun ActiveRouteRoute(
    onOpenStop: (PlannedStop) -> Unit,
    onSendToMaps: (String) -> Unit,
    onEnded: () -> Unit,
    onOpenGarage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommittedTripViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Outlives the effect: consuming the event restarts it, which would cancel the message.
    val scope = rememberCoroutineScope()
    LaunchedEffect(event) {
        when (val current = event) {
            null -> return@LaunchedEffect
            CommittedTripEvent.Replanned -> scope.launch { snackbar.showSnackbar(getString(Res.string.active_route_replanned)) }
            is CommittedTripEvent.ReplanFailed -> scope.launch { snackbar.showSnackbar(current.why.loadMessage()) }
            CommittedTripEvent.VehicleMissing -> scope.launch {
                val result = snackbar.showSnackbar(
                    garageActionVisuals(getString(Res.string.plan_vehicle_missing), getString(Res.string.plan_vehicle_missing_action)),
                )
                if (result == SnackbarResult.ActionPerformed) onOpenGarage()
            }
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
        ChargeLevelSheet(
            title = stringResource(Res.string.soc_dialog_title),
            subtitle = stringResource(Res.string.soc_dialog_car_silent),
            kind = ChargeLevelKind.NOW,
            percent = input.toIntOrNull(),
            onChange = { onSocInputChange(it.toString()) },
            onConfirm = onSocConfirm,
            onDismiss = onSocDismiss,
            confirmLabel = stringResource(Res.string.trip_soc_confirm),
        )
    }
    Column(modifier) {
        // The trip on one card: totals, arrival, and what one does to the trip itself.
        AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    listOf(
                        stringResource(Res.string.trip_summary_distance, plan.route.distanceKm.roundToInt()),
                        minutesText(plan.totalMinutes),
                        pluralStringResource(Res.plurals.trip_summary_stops, plan.stops.size, plan.stops.size),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    maxLines = 1,
                )
                Text(
                    stringResource(Res.string.trip_arr, etaText(LocalContext.current, plan.totalMinutes, LocalNow.current()), plan.arrivalSocPercent.roundToInt()),
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
                        Text(stringResource(Res.string.trip_replan), maxLines = 1, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    OutlinedButton(
                        onClick = onEnd,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = CARD_BUTTON_PADDING,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(Res.string.active_route_end), maxLines = 1)
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
