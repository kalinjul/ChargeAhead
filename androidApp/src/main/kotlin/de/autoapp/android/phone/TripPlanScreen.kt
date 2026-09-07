package de.autoapp.android.phone

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.GoButton
import de.autoapp.android.phone.components.PriceText
import de.autoapp.android.phone.components.RankBadge
import de.autoapp.android.phone.theme.tabular
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
    startSocPercent: Double?,
    isSaved: Boolean,
    isEstimate: Boolean,
    hasLocationPermission: Boolean,
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
        if (hasGoogleMapsKey) {
            TripGoogleMap(
                routePoints = plan.route.points,
                stops = plan.stops.mapIndexed { index, stop -> (index + 1) to stop.site.position },
                destination = plan.destination.position,
                hasLocationPermission = hasLocationPermission,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        } else {
            MapCanvas(
                center = null,
                pins = pins,
                routePoints = plan.route.points,
                ownPosition = startPosition,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }

        TripSummary(plan)

        if (selecting) {
            val bothPicked = selectionA != null && selectionB != null
            val hint = if (selectionA != null && !bothPicked) {
                stringResource(R.string.trip_section_hint_second)
            } else {
                stringResource(R.string.trip_section_hint)
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xFFA8C7FA),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                    }
                    .padding(9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        ) {
            item {
                TerminusRow(
                    name = startName,
                    dotColor = MaterialTheme.colorScheme.tertiary,
                    squareDot = false,
                    rightLabel = startSocPercent?.let {
                        stringResource(R.string.trip_dep_now, it.roundToInt())
                    } ?: stringResource(R.string.trip_dep_now_unknown),
                    selected = selecting && (selectionA == 0 || selectionB == 0),
                    onClick = { if (selecting) pick(0) },
                )
            }
            items(plan.stops.size) { index ->
                val stop = plan.stops[index]
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        StopCard(
                            index = index + 1,
                            stop = stop,
                            selected = selecting && (selectionA == index + 1 || selectionB == index + 1),
                            onClick = { if (selecting) pick(index + 1) else onOpenStop(stop) },
                        )
                    }
                    if (!selecting) {
                        GoButton(
                            icon = painterResource(R.drawable.ic_send),
                            contentDescription = stringResource(R.string.trip_send_stop, stop.site.name),
                            onClick = { onSendToMaps(MapsHandoff.navigateUrl(stop.site.position)) },
                            containerColor = MaterialTheme.colorScheme.surface,
                        )
                    }
                }
            }
            item {
                TerminusRow(
                    name = plan.destination.name,
                    dotColor = MaterialTheme.colorScheme.error,
                    squareDot = true,
                    rightLabel = stringResource(
                        R.string.trip_arr,
                        etaText(plan.totalMinutes),
                        plan.arrivalSocPercent.roundToInt(),
                    ),
                    selected = selecting && (selectionA == pointCount - 1 || selectionB == pointCount - 1),
                    onClick = { if (selecting) pick(pointCount - 1) },
                )
            }
            item {
                if (isEstimate) {
                    Fineprint(stringResource(R.string.trip_estimate_note))
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
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_destination),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
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
                        Text(label, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            selecting = !selecting; selectionA = null; selectionB = null
                        },
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(
                            1.dp,
                            if (selecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selecting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Text(
                            stringResource(
                                if (selecting) R.string.trip_select_cancel else R.string.trip_select_section,
                            ),
                        )
                    }
                    Surface(
                        onClick = onToggleSave,
                        shape = MaterialTheme.shapes.small,
                        color = if (isSaved) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (isSaved) Color(0xFFF2B8B2) else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(width = 44.dp, height = 40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(if (isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                                contentDescription = stringResource(R.string.trip_save),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripSummary(plan: TripPlan) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.trip_summary_distance, plan.route.distanceKm.roundToInt()),
                style = MaterialTheme.typography.headlineSmall.tabular,
            )
            Column {
                Text(
                    buildString {
                        append(minutesText(plan.totalMinutes))
                        append(" · ")
                        append(stringResource(R.string.trip_summary_stops, plan.stops.size))
                    },
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildString {
                        append(stringResource(R.string.trip_summary_charging, minutesText(plan.chargeMinutes)))
                        plan.estimatedCostEuro?.let { append(" · ≈ ${it.twoDecimals()} €") }
                    },
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun TerminusRow(
    name: String,
    dotColor: Color,
    squareDot: Boolean,
    rightLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
    ) {
        Box(Modifier.size(10.dp).background(dotColor, if (squareDot) RoundedCornerShape(2.dp) else CircleShape))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            rightLabel,
            style = MaterialTheme.typography.bodySmall.tabular,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StopCard(index: Int, stop: PlannedStop, selected: Boolean, onClick: () -> Unit) {
    AppCard(
        onClick = onClick,
        modifier = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
        ) {
            RankBadge(index, operatorColor(stop.site.operator))
            Column(Modifier.weight(1f)) {
                Text(stop.site.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(
                        R.string.trip_stop_line,
                        etaText(stop.etaMinutesFromStart - stop.chargeMinutes),
                        stop.chargeMinutes.roundToInt(),
                        stop.maxPowerKw.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            stop.quote.best?.let { PriceText(it.euroPerKwh) }
        }
    }
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
