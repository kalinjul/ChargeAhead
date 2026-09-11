package de.autoapp.android.phone

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.StationCard
import de.autoapp.android.phone.theme.tabular
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.core.MapsHandoff
import de.autoapp.shared.ui.SectionSelection
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
    hasLocationPermission: Boolean,
    // Selectable points along the trip: 0 = start, 1..n = stops, n+1 =
    // destination. The selection lives in TripViewModel; this screen only
    // renders it and reports taps.
    selection: SectionSelection,
    // The quick charge-level entry on the start row: `null` while closed.
    socInput: String?,
    onToggleSelecting: () -> Unit,
    onPickPoint: (Int) -> Unit,
    onSectionSent: () -> Unit,
    onOpenStop: (PlannedStop) -> Unit,
    onSendToMaps: (String) -> Unit,
    onToggleSave: () -> Unit,
    onReplan: () -> Unit,
    onEditStartSoc: () -> Unit,
    onSocInputChange: (String) -> Unit,
    onSocConfirm: () -> Unit,
    onSocDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selecting = selection.selecting
    val selectionA = selection.a
    val selectionB = selection.b

    val pointCount = plan.stops.size + 2

    fun pointPosition(index: Int): LatLon? = when (index) {
        0 -> startPosition ?: plan.route.points.firstOrNull()
        pointCount - 1 -> plan.destination.position
        else -> plan.stops[index - 1].site.position
    }

    val startName = stringResource(R.string.trip_start)

    socInput?.let { input ->
        StartSocDialog(
            value = input,
            onValueChange = onSocInputChange,
            onConfirm = onSocConfirm,
            onDismiss = onSocDismiss,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (hasGoogleMapsKey) {
            val mapStops = remember(plan) {
                plan.stops.mapIndexed { index, stop -> (index + 1) to stop.site.position }
            }
            TripGoogleMap(
                routePoints = plan.route.points,
                stops = mapStops,
                destination = plan.destination.position,
                hasLocationPermission = hasLocationPermission,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        } else {
            // Only the placeholder map needs the pin list — don't build it at all
            // when Google Maps is drawing, and don't rebuild it every recomposition.
            val pins = remember(plan, startPosition) {
                plan.stops.mapIndexed { index, stop ->
                    MapPin(stop.site.position, operatorColor(stop.site.operator), label = "${index + 1}")
                } + listOfNotNull(
                    startPosition?.let { MapPin(it, MapColors.position) },
                    MapPin(plan.destination.position, Color(0xFFD93025), emphasized = true),
                )
            }
            MapCanvas(
                center = null,
                pins = pins,
                routePoints = plan.route.points,
                ownPosition = startPosition,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }

        TripSummary(plan, onReplan = onReplan)

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
                    selected = selecting && selection.includes(0),
                    // Outside selection mode the start row is the shortest way
                    // to correct the charge level this plan was made from.
                    onClick = { if (selecting) onPickPoint(0) else onEditStartSoc() },
                    trailingIcon = if (selecting) null else painterResource(R.drawable.ic_pen),
                    trailingDescription = stringResource(R.string.trip_soc_edit),
                )
            }
            itemsIndexed(plan.stops, key = { _, stop -> stop.site.id }) { index, stop ->
                StationCard(
                    rank = index + 1,
                    badgeColor = operatorColor(stop.site.operator),
                    title = stop.site.operator ?: stop.site.name,
                    metaLine = stringResource(R.string.trip_stop_power, stop.maxPowerKw.roundToInt()),
                    address = ChargeStopFormatter.addressLine(stop.site),
                    // etaMinutesFromStart counts to departure, so the charge
                    // time comes off it for the arrival — and the SOC next to
                    // it is the one on arrival, before charging.
                    extraLine = stringResource(
                        R.string.trip_stop_eta_charge,
                        etaText(stop.etaMinutesFromStart - stop.chargeMinutes),
                        stop.arrivalSocPercent.roundToInt(),
                        stop.chargeMinutes.roundToInt(),
                    ),
                    selected = selecting && selection.includes(index + 1),
                    onClick = { if (selecting) onPickPoint(index + 1) else onOpenStop(stop) },
                    // Section-select mode repurposes the card tap; hide the send
                    // button so the two tap targets can't be confused.
                    onSend = if (selecting) null else ({ onSendToMaps(MapsHandoff.navigateUrl(stop.site.position)) }),
                    sendContentDescription = stringResource(R.string.trip_send_stop, stop.site.name),
                )
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
                    selected = selecting && selection.includes(pointCount - 1),
                    onClick = { if (selecting) onPickPoint(pointCount - 1) },
                )
            }
            item {
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
                            // The origin stays "my location" even for a section
                            // that starts further along: Google Maps only
                            // navigates from where the driver actually is, and
                            // a fixed origin turns the hand-off into a route
                            // preview it refuses to start. The section's own
                            // first point becomes the first waypoint instead.
                            val url = MapsHandoff.directionsUrl(
                                origin = null,
                                destination = pointPosition(toIndex) ?: plan.destination.position,
                                waypoints = (maxOf(fromIndex, 1) until toIndex).mapNotNull { pointPosition(it) },
                            )
                            onSendToMaps(url)
                            onSectionSent()
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_destination),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            stringResource(R.string.trip_send_maps),
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = onToggleSelecting,
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
private fun TripSummary(plan: TripPlan, onReplan: () -> Unit) {
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
                        append(pluralStringResource(R.plurals.trip_summary_stops, plan.stops.size, plan.stops.size))
                    },
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildString {
                        append(stringResource(R.string.trip_summary_charging, minutesText(plan.chargeMinutes)))
                    },
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            // Same destination, fresh start: the plan sheet reopens with it
            // already picked, so only the charge level and the filters are
            // left to change.
            Surface(
                onClick = onReplan,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_route),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(stringResource(R.string.trip_replan), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The quick charge-level entry behind the start row. Confirming it re-plans:
 * every stop after it depends on the level, so there is nothing to patch in
 * place.
 */
@Composable
private fun StartSocDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val percent = value.toIntOrNull()?.takeIf { it in 1..100 }
    // "Quick" only holds if the keyboard is already up: the driver opened
    // this to type a number, not to tap a field first.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_soc_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                isError = percent == null,
                suffix = { Text("%") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = percent != null) {
                Text(stringResource(R.string.trip_soc_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.trip_soc_cancel)) }
        },
    )
}

@Composable
private fun TerminusRow(
    name: String,
    dotColor: Color,
    squareDot: Boolean,
    rightLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailingIcon: Painter? = null,
    trailingDescription: String? = null,
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
        trailingIcon?.let {
            Icon(
                it,
                contentDescription = trailingDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
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
