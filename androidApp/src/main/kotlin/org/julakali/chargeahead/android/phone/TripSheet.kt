package org.julakali.chargeahead.android.phone

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.android.phone.components.RankBadge
import org.julakali.chargeahead.android.phone.components.SocEditDialog
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.core.MapsHandoff
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.ui.ARRIVAL_SOC_RANGE
import org.julakali.chargeahead.shared.ui.SectionSelection
import kotlin.math.roundToInt

/** How the stops are laid out in the sheet. Tiles fit the peek and never expand. */
enum class TripListLayout { LIST, TILES }

/**
 * The planned trip as sheet content: section hint, then the stops as an
 * itinerary rail (or a tile row), the send actions at the end.
 *
 * Section selection works on the point sequence start → stops → destination:
 * tap two of them and exactly that section goes to Maps.
 */
@Composable
fun TripSheetContent(
    plan: TripPlan,
    startPosition: LatLon?,
    startSocPercent: Double?,
    isSaved: Boolean,
    layout: TripListLayout,
    // Selectable points along the trip: 0 = start, 1..n = stops, n+1 = destination.
    selection: SectionSelection,
    // The quick charge-level entry on the start row: `null` while closed.
    socInput: String?,
    // The same on the destination row, for the level to arrive with.
    arrivalSocInput: String?,
    onToggleSelecting: () -> Unit,
    onPickPoint: (Int) -> Unit,
    onSectionSent: () -> Unit,
    onOpenStop: (PlannedStop) -> Unit,
    onSendToMaps: (String) -> Unit,
    onToggleSave: () -> Unit,
    onEditStartSoc: () -> Unit,
    onSocInputChange: (String) -> Unit,
    onSocConfirm: () -> Unit,
    onSocDismiss: () -> Unit,
    onEditArrivalSoc: () -> Unit,
    onArrivalSocInputChange: (String) -> Unit,
    onArrivalSocConfirm: () -> Unit,
    onArrivalSocDismiss: () -> Unit,
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

    // The quick charge-level entry behind the start row. Confirming it re-plans.
    socInput?.let { input ->
        SocEditDialog(
            value = input,
            title = stringResource(R.string.soc_dialog_title),
            confirmLabel = stringResource(R.string.trip_soc_confirm),
            onValueChange = onSocInputChange,
            onConfirm = onSocConfirm,
            onDismiss = onSocDismiss,
        )
    }

    // The level to arrive with, edited on the destination row. Confirming re-plans.
    arrivalSocInput?.let { input ->
        SocEditDialog(
            value = input,
            title = stringResource(R.string.garage_arrival_title),
            confirmLabel = stringResource(R.string.trip_soc_confirm),
            onValueChange = onArrivalSocInputChange,
            onConfirm = onArrivalSocConfirm,
            onDismiss = onArrivalSocDismiss,
            valueRange = ARRIVAL_SOC_RANGE.first.toFloat()..ARRIVAL_SOC_RANGE.last.toFloat(),
        )
    }

    Column(modifier = modifier) {
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

        AnimatedContent(
            targetState = layout,
            transitionSpec = {
                // The sheet itself glides; the content only cross-fades, anchored at the top.
                fadeIn(tween(200)).togetherWith(fadeOut(tween(120))).using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.TopCenter,
            label = "trip list layout",
            // The list fills what the sheet offers; the tile row takes its own height.
            modifier = if (layout == TripListLayout.LIST) Modifier.weight(1f) else Modifier,
        ) { shown ->
            when (shown) {
                TripListLayout.LIST -> StopRail(
                    plan = plan,
                    startSocPercent = startSocPercent,
                    selection = selection,
                    onPickPoint = onPickPoint,
                    onOpenStop = onOpenStop,
                    onEditStartSoc = onEditStartSoc,
                    onEditArrivalSoc = onEditArrivalSoc,
                    modifier = Modifier.fillMaxSize(),
                )
                TripListLayout.TILES -> StopTiles(
                    plan = plan,
                    selection = selection,
                    onPickPoint = onPickPoint,
                    onOpenStop = onOpenStop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Button(
                onClick = {
                    val lo = minOf(selectionA ?: 0, selectionB ?: (pointCount - 1))
                    val hi = maxOf(selectionA ?: 0, selectionB ?: (pointCount - 1))
                    val useSelection = selecting && selectionA != null && selectionB != null
                    val fromIndex = if (useSelection) lo else 0
                    val toIndex = if (useSelection) hi else pointCount - 1
                    // The origin stays "my location": with a fixed origin, Maps
                    // only previews. The section's first point becomes a waypoint.
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

/** Start, stops and destination on one vertical line. */
@Composable
private fun StopRail(
    plan: TripPlan,
    startSocPercent: Double?,
    selection: SectionSelection,
    onPickPoint: (Int) -> Unit,
    onOpenStop: (PlannedStop) -> Unit,
    onEditStartSoc: () -> Unit,
    onEditArrivalSoc: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selecting = selection.selecting
    val last = plan.stops.size + 1

    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item(key = "start") {
            RailRow(
                index = 0,
                last = last,
                selected = selecting && selection.includes(0),
                selectionShape = selection.spanShape(0),
                onClick = { if (selecting) onPickPoint(0) else onEditStartSoc() },
                dot = { TerminusDot(MaterialTheme.colorScheme.tertiary, square = false) },
                trailing = painterResource(R.drawable.ic_pen),
                trailingDescription = stringResource(R.string.trip_soc_edit),
            ) {
                Text(stringResource(R.string.trip_start), style = MaterialTheme.typography.titleSmall)
                MetaLine(
                    startSocPercent?.let { stringResource(R.string.trip_dep_now, it.roundToInt()) }
                        ?: stringResource(R.string.trip_dep_now_unknown),
                )
            }
        }
        itemsIndexed(plan.stops, key = { _, stop -> stop.site.id }) { i, stop ->
            val index = i + 1
            RailRow(
                index = index,
                last = last,
                selected = selecting && selection.includes(index),
                selectionShape = selection.spanShape(index),
                onClick = { if (selecting) onPickPoint(index) else onOpenStop(stop) },
                dot = { RankBadge(index, operatorColor(stop.site.operator)) },
            ) {
                Text(
                    stop.site.operator ?: stop.site.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLine(
                    stringResource(
                        R.string.trip_stop_charge,
                        stop.maxPowerKw.roundToInt(),
                        stop.arrivalSocPercent.roundToInt(),
                        stop.departureSocPercent.roundToInt(),
                    ),
                )
                MetaLine(
                    stringResource(
                        R.string.trip_stop_times,
                        etaText(stop.arrivalMinutesFromStart),
                        etaText(stop.arrivalMinutesFromStart + stop.chargeMinutes),
                    ),
                )
            }
        }
        item(key = "destination") {
            RailRow(
                index = last,
                last = last,
                selected = selecting && selection.includes(last),
                selectionShape = selection.spanShape(last),
                onClick = { if (selecting) onPickPoint(last) else onEditArrivalSoc() },
                dot = { TerminusDot(MaterialTheme.colorScheme.error, square = true) },
                trailing = painterResource(R.drawable.ic_pen),
                trailingDescription = stringResource(R.string.trip_arrival_soc_edit),
            ) {
                Text(
                    plan.destination.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLine(
                    stringResource(R.string.trip_arr, etaText(plan.totalMinutes), plan.arrivalSocPercent.roundToInt()),
                )
            }
        }
    }
}

/** One rail row: the line segment, the dot, the text; a picked section tints the row. */
@Composable
private fun RailRow(
    index: Int,
    last: Int,
    selected: Boolean,
    /** Rounded only where the picked span begins or ends. */
    selectionShape: Shape,
    onClick: () -> Unit,
    dot: @Composable () -> Unit,
    trailing: androidx.compose.ui.graphics.painter.Painter? = null,
    trailingDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            // The rail box fills the row's height, so the row needs one.
            .height(IntrinsicSize.Min)
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, selectionShape) else Modifier,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .width(RAIL_WIDTH)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val dotCenter = DOT_TOP.toPx() + DOT_SIZE.toPx() / 2
                    val top = if (index == 0) dotCenter else 0f
                    val bottom = if (index == last) dotCenter else size.height
                    drawLine(lineColor, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
                }
                .padding(top = DOT_TOP),
            contentAlignment = Alignment.TopCenter,
        ) {
            // A ring in the surface color lifts the dot off the line.
            Box(
                Modifier.size(DOT_SIZE).background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { dot() }
        }
        Column(Modifier.weight(1f)) {
            Column(
                Modifier.padding(top = ROW_PADDING, bottom = ROW_PADDING, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                content()
            }
            if (index != last) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
        trailing?.let {
            Icon(
                it,
                contentDescription = trailingDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, end = 6.dp).size(14.dp),
            )
        }
    }
}

/** The tint reads as one block: corners only at the span's ends. */
@Composable
private fun SectionSelection.spanShape(index: Int): Shape {
    val radius = 10.dp
    val top = if (includes(index - 1)) 0.dp else radius
    val bottom = if (includes(index + 1)) 0.dp else radius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun TerminusDot(color: Color, square: Boolean) {
    Box(Modifier.size(12.dp).background(color, if (square) RoundedCornerShape(2.dp) else CircleShape))
}

@Composable
private fun MetaLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The stops as a swipeable row for the collapsed sheet. */
@Composable
private fun StopTiles(
    plan: TripPlan,
    selection: SectionSelection,
    onPickPoint: (Int) -> Unit,
    onOpenStop: (PlannedStop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selecting = selection.selecting
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier,
    ) {
        itemsIndexed(plan.stops, key = { _, stop -> stop.site.id }) { i, stop ->
            val index = i + 1
            val selected = selecting && selection.includes(index)
            Surface(
                onClick = { if (selecting) onPickPoint(index) else onOpenStop(stop) },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.width(136.dp),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RankBadge(index, operatorColor(stop.site.operator))
                    Text(
                        stop.site.operator ?: stop.site.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetaLine(stringResource(R.string.trip_tile_charge, stop.maxPowerKw.roundToInt(), stop.chargeMinutes.roundToInt()))
                    MetaLine(stringResource(R.string.trip_tile_arrival, etaText(stop.arrivalMinutesFromStart)))
                }
            }
        }
    }
}

/** Charging total, the layout switch and the way back into the search; the numbers sit in the header. */
@Composable
fun TripSummary(plan: TripPlan, layout: TripListLayout, onToggleLayout: () -> Unit, onReplan: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(start = 16.dp, end = 10.dp, top = 2.dp, bottom = 2.dp)
                .fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.trip_summary_charging, minutesText(plan.chargeMinutes)),
                style = MaterialTheme.typography.bodySmall.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            // The search reopens with the same destination typed in.
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
            IconButton(onClick = onToggleLayout) {
                Icon(
                    if (layout == TripListLayout.LIST) Icons.Outlined.ViewCarousel else Icons.AutoMirrored.Outlined.ViewList,
                    contentDescription = stringResource(
                        if (layout == TripListLayout.LIST) R.string.trip_layout_tiles else R.string.trip_layout_list,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** "312 km · 3 h 10 min · 2 Stopps" for the destination header. */
@Composable
fun TripPlan.headerLine(): String = listOf(
    stringResource(R.string.trip_summary_distance, route.distanceKm.roundToInt()),
    minutesText(totalMinutes),
    pluralStringResource(R.plurals.trip_summary_stops, stops.size, stops.size),
).joinToString(" · ")

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

/** The collapsed list shows the first stops; the tile row is only as tall as it needs. */
@Composable
fun tripPeekHeight(layout: TripListLayout): Dp = when (layout) {
    TripListLayout.LIST -> (LocalConfiguration.current.screenHeightDp / 3).dp
    TripListLayout.TILES -> TILES_PEEK + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
}

private val RAIL_WIDTH = 36.dp

/** Summary row, one row of tiles, the action row. */
private val TILES_PEEK = 236.dp
private val DOT_SIZE = 28.dp
private val ROW_PADDING = 10.dp

/** Puts the dot's centre on the title line. */
private val DOT_TOP = ROW_PADDING - 2.dp
