package de.autoapp.android.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.shared.core.MapsHandoff
import de.autoapp.shared.core.PlannedStop
import de.autoapp.shared.core.TripPlan
import de.autoapp.shared.domain.LatLon
import kotlin.math.roundToInt

/**
 * The planned trip: placeholder map on top, summary, then the stops as a
 * list with the send actions at its end — scrolling to them is deliberate,
 * the route itself is the content.
 *
 * Section selection works on the point sequence start → stops → destination:
 * tap two of them and exactly that section goes to Maps. Everything the
 * driver sees here was computed by the shared planner; this screen only
 * arranges it.
 */
@Composable
fun TripPlanScreen(
    plan: TripPlan,
    startPosition: LatLon?,
    isSaved: Boolean,
    isEstimate: Boolean,
    onOpenStop: (PlannedStop) -> Unit,
    onSendToMaps: (String) -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selectable points along the trip: 0 = start, 1..n = stops, n+1 = destination.
    var selecting by remember(plan) { mutableStateOf(false) }
    var selectionA by remember(plan) { mutableStateOf<Int?>(null) }
    var selectionB by remember(plan) { mutableStateOf<Int?>(null) }

    val pointCount = plan.stops.size + 2
    fun pointName(index: Int): String = when (index) {
        0 -> ""
        pointCount - 1 -> plan.destination.name
        else -> plan.stops[index - 1].site.name
    }

    fun pointPosition(index: Int): LatLon? = when (index) {
        0 -> startPosition ?: plan.route.points.firstOrNull()
        pointCount - 1 -> plan.destination.position
        else -> plan.stops[index - 1].site.position
    }

    fun pick(index: Int) {
        when {
            selectionA == null -> selectionA = index
            selectionB == null && index != selectionA -> selectionB = index
            else -> { selectionA = index; selectionB = null }
        }
    }

    val startName = stringResource(R.string.trip_start)
    val pins = plan.stops.mapIndexed { index, stop ->
        MapPin(stop.site.position, operatorColor(stop.site.operator), label = "${index + 1}")
    } + listOfNotNull(
        startPosition?.let { MapPin(it, MapColors.position) },
        MapPin(plan.destination.position, androidx.compose.ui.graphics.Color(0xFFD93025), emphasized = true),
    )

    Column(modifier = modifier.fillMaxSize()) {
        MapCanvas(
            center = null,
            pins = pins,
            routePoints = plan.route.points,
            ownPosition = startPosition,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )

        TripSummary(plan)

        if (selecting) {
            Text(
                stringResource(R.string.trip_section_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        ) {
            item {
                TerminusRow(
                    name = startName,
                    selected = selecting && (selectionA == 0 || selectionB == 0),
                    onClick = { if (selecting) pick(0) },
                )
            }
            items(plan.stops.size) { index ->
                val stop = plan.stops[index]
                StopCard(
                    index = index + 1,
                    stop = stop,
                    selected = selecting && (selectionA == index + 1 || selectionB == index + 1),
                    onClick = { if (selecting) pick(index + 1) else onOpenStop(stop) },
                )
            }
            item {
                TerminusRow(
                    name = plan.destination.name,
                    selected = selecting && (selectionA == pointCount - 1 || selectionB == pointCount - 1),
                    onClick = { if (selecting) pick(pointCount - 1) },
                )
            }
            item {
                if (isEstimate) {
                    Text(
                        stringResource(R.string.trip_estimate_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Button(
                        onClick = {
                            val lo = minOf(selectionA ?: 0, selectionB ?: (pointCount - 1))
                            val hi = maxOf(selectionA ?: 0, selectionB ?: (pointCount - 1))
                            val useSelection = selecting && selectionA != null && selectionB != null
                            val fromIndex = if (useSelection) lo else 0
                            val toIndex = if (useSelection) hi else pointCount - 1
                            val url = MapsHandoff.directionsUrl(
                                origin = if (fromIndex == 0) null else pointPosition(fromIndex),
                                destination = pointPosition(toIndex) ?: plan.destination.position,
                                waypoints = ((fromIndex + 1) until toIndex).mapNotNull { pointPosition(it) },
                            )
                            onSendToMaps(url)
                            selecting = false; selectionA = null; selectionB = null
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        val label = if (selecting && selectionA != null && selectionB != null) {
                            val lo = minOf(selectionA!!, selectionB!!)
                            val hi = maxOf(selectionA!!, selectionB!!)
                            stringResource(
                                R.string.trip_send_selected,
                                pointName(lo).ifEmpty { startName },
                                pointName(hi),
                            )
                        } else {
                            stringResource(R.string.trip_send_maps)
                        }
                        Text(label, maxLines = 1)
                    }
                    OutlinedButton(onClick = {
                        selecting = !selecting; selectionA = null; selectionB = null
                    }) {
                        Text(
                            stringResource(
                                if (selecting) R.string.trip_select_cancel else R.string.trip_select_section,
                            ),
                        )
                    }
                    IconButton(onClick = onToggleSave) {
                        Icon(
                            painter = painterResource(
                                if (isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                            ),
                            contentDescription = stringResource(R.string.trip_save),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripSummary(plan: TripPlan) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.trip_summary_distance, plan.route.distanceKm.roundToInt()),
                style = MaterialTheme.typography.headlineSmall,
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    stringResource(
                        R.string.trip_summary_time,
                        minutesText(plan.totalMinutes),
                        minutesText(plan.chargeMinutes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row {
                    plan.estimatedCostEuro?.let {
                        Text(
                            stringResource(R.string.trip_summary_cost, it.twoDecimals()) + " · ",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        stringResource(R.string.trip_summary_arrival, plan.arrivalSocPercent.roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StopCard(index: Int, stop: PlannedStop, selected: Boolean, onClick: () -> Unit) {
    Card(
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$index · ${stop.site.name}", style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        R.string.trip_stop_line,
                        etaText(stop.etaMinutesFromStart),
                        stop.chargeMinutes.roundToInt(),
                        stop.maxPowerKw.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            stop.quote.best?.let { best ->
                Text(
                    stringResource(R.string.cn_price, best.euroPerKwh.twoDecimals()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun TerminusRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleSmall,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

/** "9 h 16 min" or "42 min" — durations, not clock times. */
internal fun minutesText(minutes: Double): String {
    val total = minutes.roundToInt()
    val hours = total / 60
    val rest = total % 60
    return if (hours > 0) "$hours h $rest min" else "$rest min"
}

/** Wall-clock arrival, from now plus the ETA offset. */
internal fun etaText(minutesFromStart: Double): String {
    val eta = java.time.LocalTime.now().plusMinutes(minutesFromStart.toLong())
    return "%02d:%02d".format(eta.hour, eta.minute)
}
