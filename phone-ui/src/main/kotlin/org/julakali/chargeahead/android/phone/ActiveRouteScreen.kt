package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.ui.CommittedTripEvent
import org.julakali.chargeahead.shared.ui.CommittedTripViewModel
import org.julakali.chargeahead.shared.ui.SectionSelection
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
            onReplan = viewModel::replan,
            onEnd = viewModel::endTrip,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
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
) {
    val plan = trip.plan
    Column(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                plan.destination.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(
                    stringResource(R.string.trip_summary_distance, plan.route.distanceKm.roundToInt()),
                    minutesText(plan.totalMinutes),
                    pluralStringResource(R.plurals.trip_summary_stops, plan.stops.size, plan.stops.size),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TripSheetContent(
            plan = plan,
            startSocPercent = trip.startSocPercent,
            layout = TripListLayout.LIST,
            selection = selection,
            socInput = null,
            arrivalSocInput = null,
            onToggleSelecting = onToggleSelecting,
            onPickPoint = onPickPoint,
            onSectionSent = onSectionSent,
            onOpenStop = onOpenStop,
            onSendToMaps = onSendToMaps,
            onEditStartSoc = {},
            onSocInputChange = {},
            onSocConfirm = {},
            onSocDismiss = {},
            onEditArrivalSoc = {},
            onArrivalSocInputChange = {},
            onArrivalSocConfirm = {},
            onArrivalSocDismiss = {},
            socEditable = false,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).navigationBarsPadding(),
        ) {
            OutlinedButton(
                onClick = onReplan,
                enabled = !planning,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            ) {
                if (planning) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_route), contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Text(stringResource(R.string.trip_replan), maxLines = 1, modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = onEnd,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.active_route_end), maxLines = 1)
            }
        }
    }
}
