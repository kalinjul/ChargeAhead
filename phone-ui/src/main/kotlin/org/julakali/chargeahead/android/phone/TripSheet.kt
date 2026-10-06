package org.julakali.chargeahead.android.phone

import android.content.Context
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.ViewWeek
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
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.theme.ChargeAheadMotion
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.components.RankBadge
import org.julakali.chargeahead.android.phone.components.ChargeLevelKind
import org.julakali.chargeahead.android.phone.components.ChargeLevelSheet
import org.julakali.chargeahead.android.phone.theme.ChargeAheadColors
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.SectionSelection
import org.julakali.chargeahead.shared.ui.TripListLayout
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.garage_arrival_sheet_hint
import org.julakali.chargeahead.shared.resources.garage_arrival_title
import org.julakali.chargeahead.shared.resources.soc_dialog_car_silent
import org.julakali.chargeahead.shared.resources.soc_dialog_title
import org.julakali.chargeahead.shared.resources.trip_arr
import org.julakali.chargeahead.shared.resources.trip_arrival_soc_edit
import org.julakali.chargeahead.shared.resources.trip_dep_now
import org.julakali.chargeahead.shared.resources.trip_dep_now_unknown
import org.julakali.chargeahead.shared.resources.trip_duration_hours_minutes
import org.julakali.chargeahead.shared.resources.trip_duration_minutes
import org.julakali.chargeahead.shared.resources.trip_layout_list
import org.julakali.chargeahead.shared.resources.trip_layout_tiles
import org.julakali.chargeahead.shared.resources.trip_replan
import org.julakali.chargeahead.shared.resources.trip_section_hint
import org.julakali.chargeahead.shared.resources.trip_section_hint_second
import org.julakali.chargeahead.shared.resources.trip_select_cancel
import org.julakali.chargeahead.shared.resources.trip_select_section
import org.julakali.chargeahead.shared.resources.trip_send_maps
import org.julakali.chargeahead.shared.resources.trip_soc_confirm
import org.julakali.chargeahead.shared.resources.trip_soc_edit
import org.julakali.chargeahead.shared.resources.trip_start
import org.julakali.chargeahead.shared.resources.trip_stop_charge
import org.julakali.chargeahead.shared.resources.trip_stop_times
import org.julakali.chargeahead.shared.resources.trip_summary_charging
import org.julakali.chargeahead.shared.resources.trip_summary_distance
import org.julakali.chargeahead.shared.resources.trip_summary_stops
import org.julakali.chargeahead.shared.resources.trip_tile_arrival
import org.julakali.chargeahead.shared.resources.trip_tile_charge

/** The charge-level editors behind the trip's start and destination rows. */
class SocEditing(
    /** The start-level editor's input; `null` while closed. */
    val socInput: String?,
    /** The arrival-level editor's input; `null` while closed. */
    val arrivalSocInput: String?,
    /** The start-level editor opened because "Neu planen" had no car reading; the dialog says so. */
    val askedForReplan: Boolean,
    val onEditStartSoc: () -> Unit,
    val onSocInputChange: (String) -> Unit,
    val onSocConfirm: () -> Unit,
    val onSocDismiss: () -> Unit,
    val onEditArrivalSoc: () -> Unit,
    val onArrivalSocInputChange: (String) -> Unit,
    val onArrivalSocConfirm: () -> Unit,
    val onArrivalSocDismiss: () -> Unit,
)

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
    startSocPercent: Double?,
    layout: TripListLayout,
    // Selectable points along the trip: 0 = start, 1..n = stops, n+1 = destination.
    selection: SectionSelection,
    onToggleSelecting: () -> Unit,
    onPickPoint: (Int) -> Unit,
    onSectionSent: () -> Unit,
    onOpenStop: (PlannedStop) -> Unit,
    onSendToMaps: () -> Unit,
    /** The charge-level editors behind the start and destination rows; `null` makes both rows read-only. */
    socEditing: SocEditing?,
    modifier: Modifier = Modifier,
) {
    val selecting = selection.selecting
    val selectionA = selection.a
    val selectionB = selection.b

    // The quick charge-level entry behind the start row. Confirming it re-plans.
    socEditing?.socInput?.let { input ->
        ChargeLevelSheet(
            title = stringResource(Res.string.soc_dialog_title),
            subtitle = if (socEditing.askedForReplan) stringResource(Res.string.soc_dialog_car_silent) else null,
            kind = ChargeLevelKind.NOW,
            percent = input.toIntOrNull(),
            onChange = { socEditing.onSocInputChange(it.toString()) },
            onConfirm = socEditing.onSocConfirm,
            onDismiss = socEditing.onSocDismiss,
            confirmLabel = stringResource(Res.string.trip_soc_confirm),
        )
    }

    // The level to arrive with, edited on the destination row. Confirming re-plans.
    socEditing?.arrivalSocInput?.let { input ->
        ChargeLevelSheet(
            title = stringResource(Res.string.garage_arrival_title),
            subtitle = stringResource(Res.string.garage_arrival_sheet_hint),
            kind = ChargeLevelKind.ARRIVAL,
            percent = input.toIntOrNull(),
            onChange = { socEditing.onArrivalSocInputChange(it.toString()) },
            onConfirm = socEditing.onArrivalSocConfirm,
            onDismiss = socEditing.onArrivalSocDismiss,
            confirmLabel = stringResource(Res.string.trip_soc_confirm),
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (selecting) {
            val sectionOutline = ChargeAheadColors.sectionOutline
            val bothPicked = selectionA != null && selectionB != null
            val hint = if (selectionA != null && !bothPicked) {
                stringResource(Res.string.trip_section_hint_second)
            } else {
                stringResource(Res.string.trip_section_hint)
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                    .drawBehind {
                        drawRoundRect(
                            color = sectionOutline,
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

        val actions: @Composable () -> Unit = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            ) {
                Button(
                    onClick = {
                        onSendToMaps()
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
                        stringResource(Res.string.trip_send_maps),
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
                            if (selecting) Res.string.trip_select_cancel else Res.string.trip_select_section,
                        ),
                    )
                }
            }
        }

        val slide = ChargeAheadMotion.spatial<IntOffset>()
        val fade = ChargeAheadMotion.effects<Float>()
        AnimatedContent(
            targetState = layout,
            transitionSpec = {
                // Tiles come in from the right, the list from the left; both fill the same box.
                val forward = targetState == TripListLayout.TILES
                (slideInHorizontally(slide) { if (forward) it else -it } + fadeIn(fade))
                    .togetherWith(slideOutHorizontally(slide) { if (forward) -it else it } + fadeOut(fade))
            },
            contentAlignment = Alignment.TopStart,
            label = "trip list layout",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { shown ->
            when (shown) {
                TripListLayout.LIST -> StopRail(
                    plan = plan,
                    startSocPercent = startSocPercent,
                    selection = selection,
                    onPickPoint = onPickPoint,
                    onOpenStop = onOpenStop,
                    socEditing = socEditing,
                    modifier = Modifier.fillMaxSize(),
                )
                TripListLayout.TILES -> StopTiles(
                    plan = plan,
                    selection = selection,
                    onPickPoint = onPickPoint,
                    onOpenStop = onOpenStop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Same buttons in both layouts, so they stay put while the content slides.
        actions()
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
    socEditing: SocEditing?,
    modifier: Modifier = Modifier,
) {
    val selecting = selection.selecting
    val last = plan.stops.size + 1
    val pen = socEditing?.let { painterResource(R.drawable.ic_pen) }
    val now = LocalNow.current()
    val context = LocalContext.current

    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item(key = "start") {
            RailRow(
                index = 0,
                last = last,
                selected = selecting && selection.includes(0),
                selectionShape = selection.spanShape(0),
                onClick = { if (selecting) onPickPoint(0) else socEditing?.onEditStartSoc?.invoke() },
                dot = { TerminusDot(MaterialTheme.colorScheme.tertiary, square = false) },
                trailing = pen,
                trailingDescription = stringResource(Res.string.trip_soc_edit),
            ) {
                Text(stringResource(Res.string.trip_start), style = MaterialTheme.typography.titleSmall)
                MetaLine(
                    startSocPercent?.let { stringResource(Res.string.trip_dep_now, it.roundToInt()) }
                        ?: stringResource(Res.string.trip_dep_now_unknown),
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
                dot = { RankBadge(index, operatorColor(stop.site)) },
            ) {
                Text(
                    stop.site.operator ?: stop.site.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLine(
                    stringResource(
                        Res.string.trip_stop_charge,
                        stop.maxPowerKw.roundToInt(),
                        stop.arrivalSocPercent.roundToInt(),
                        stop.departureSocPercent.roundToInt(),
                    ),
                )
                MetaLine(
                    stringResource(
                        Res.string.trip_stop_times,
                        etaText(context, stop.arrivalMinutesFromStart, now),
                        etaText(context, stop.arrivalMinutesFromStart + stop.chargeMinutes, now),
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
                onClick = { if (selecting) onPickPoint(last) else socEditing?.onEditArrivalSoc?.invoke() },
                dot = { TerminusDot(MaterialTheme.colorScheme.error, square = true) },
                trailing = pen,
                trailingDescription = stringResource(Res.string.trip_arrival_soc_edit),
            ) {
                Text(
                    plan.destination.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLine(
                    stringResource(Res.string.trip_arr, etaText(context, plan.totalMinutes, now), plan.arrivalSocPercent.roundToInt()),
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
    val now = LocalNow.current()
    val context = LocalContext.current
    // Centred in the room the list would take, so the action row stays where it was.
    Box(modifier, contentAlignment = Alignment.Center) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            itemsIndexed(plan.stops, key = { _, stop -> stop.site.id }) { i, stop ->
                val index = i + 1
                val selected = selecting && selection.includes(index)
                AppCard(
                    onClick = { if (selecting) onPickPoint(index) else onOpenStop(stop) },
                    selected = selected,
                    modifier = Modifier.width(136.dp),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RankBadge(index, operatorColor(stop.site))
                        Text(
                            stop.site.operator ?: stop.site.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MetaLine(stringResource(Res.string.trip_tile_charge, stop.maxPowerKw.roundToInt(), stop.chargeMinutes.roundToInt()))
                        MetaLine(stringResource(Res.string.trip_tile_arrival, etaText(context, stop.arrivalMinutesFromStart, now)))
                    }
                }
            }
        }
    }
}

/** Stops and charging total, the layout switch and the way back into the search; distance and time sit in the header. */
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
                listOf(
                    pluralStringResource(Res.plurals.trip_summary_stops, plan.stops.size, plan.stops.size),
                    stringResource(Res.string.trip_summary_charging, minutesText(plan.chargeMinutes)),
                ).joinToString(" · "),
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
                    Text(stringResource(Res.string.trip_replan), style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = onToggleLayout) {
                Icon(
                    if (layout == TripListLayout.LIST) Icons.Outlined.ViewWeek else Icons.AutoMirrored.Outlined.FormatListBulleted,
                    contentDescription = stringResource(
                        if (layout == TripListLayout.LIST) Res.string.trip_layout_tiles else Res.string.trip_layout_list,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    // ViewWeek fills its grid edge to edge; a touch smaller matches the list glyph's weight.
                    modifier = Modifier.size(if (layout == TripListLayout.LIST) 16.dp else 18.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** "312 km · 3h 10m" for the destination header. */
@Composable
fun TripPlan.headerLine(): String = listOf(
    stringResource(Res.string.trip_summary_distance, route.distanceKm.roundToInt()),
    minutesText(totalMinutes),
).joinToString(" · ")

/** "9h 16m" or "42 min" — durations, not clock times; one shape for the header and the sheet. */
@Composable
fun minutesText(minutes: Double): String {
    val total = minutes.roundToInt()
    val hours = total / 60
    val rest = total % 60
    return if (hours > 0) {
        stringResource(Res.string.trip_duration_hours_minutes, hours, rest)
    } else {
        stringResource(Res.string.trip_duration_minutes, rest)
    }
}

/** The clock the trip rows read; tests pin it so times don't drift. */
val LocalNow = staticCompositionLocalOf<() -> LocalTime> { { LocalTime.now() } }

/** Wall-clock arrival, from [now] plus the ETA offset, in the device's 12h/24h style. */
fun etaText(context: Context, minutesFromStart: Double, now: LocalTime): String {
    val locale = context.resources.configuration.locales[0]
    // "j" lets ICU pick the locale's hour cycle; DateFormat.is24HourFormat would ask Locale.getDefault() instead.
    val skeleton = when (Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24)) {
        "24" -> "Hm"
        "12" -> "hm"
        else -> "jm"
    }
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return now.plusMinutes(minutesFromStart.toLong()).format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** The collapsed sheet shows the first stops, in either layout. */
@Composable
fun tripPeekHeight(): Dp = (LocalConfiguration.current.screenHeightDp / 3).dp

private val RAIL_WIDTH = 36.dp

private val DOT_SIZE = 28.dp
private val ROW_PADDING = 10.dp

/** Puts the dot's centre on the title line. */
private val DOT_TOP = ROW_PADDING - 2.dp
