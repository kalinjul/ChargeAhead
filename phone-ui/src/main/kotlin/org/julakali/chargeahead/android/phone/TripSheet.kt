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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.theme.ChargeAheadMotion
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.TripLeg
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.SectionSelection
import org.julakali.chargeahead.shared.ui.TripListLayout
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.trip_unreachable_summary
import org.julakali.chargeahead.shared.ui.TripUiState
import org.julakali.chargeahead.shared.resources.garage_arrival_sheet_hint
import org.julakali.chargeahead.shared.resources.garage_arrival_title
import org.julakali.chargeahead.shared.resources.garage_kw
import org.julakali.chargeahead.shared.resources.garage_percent
import org.julakali.chargeahead.shared.resources.soc_dialog_car_silent
import org.julakali.chargeahead.shared.resources.soc_dialog_title
import org.julakali.chargeahead.shared.resources.trip_arrival_soc_edit
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
import org.julakali.chargeahead.shared.resources.trip_stop_missing
import org.julakali.chargeahead.shared.resources.trip_start
import org.julakali.chargeahead.shared.resources.trip_summary_charging
import org.julakali.chargeahead.shared.resources.trip_summary_distance
import org.julakali.chargeahead.shared.resources.trip_summary_stops
import org.julakali.chargeahead.shared.resources.trip_tile_arrival
import org.julakali.chargeahead.shared.resources.trip_tile_charge
import org.julakali.chargeahead.shared.forUi

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
 * The planned trip as sheet content: section hint, then the stops as
 * cards (or a tile row), the send actions at the end.
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
    /** What the stop and arrival times count from: now for a plan, the commit for a trip under way. */
    departure: LocalTime = LocalNow.current(),
) {
    val selecting = selection.selecting
    val selectionA = selection.a
    val selectionB = selection.b

    socEditing?.let { SocEditors(it) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (plan.needsReplan) {
            Text(
                stringResource(Res.string.trip_stop_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.extraSmall)
                    .padding(9.dp),
            )
        }
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
                TripListLayout.LIST -> StopCards(
                    plan = plan,
                    startSocPercent = startSocPercent,
                    selection = selection,
                    onPickPoint = onPickPoint,
                    onOpenStop = onOpenStop,
                    socEditing = socEditing,
                    departure = departure,
                    modifier = Modifier.fillMaxSize(),
                )
                TripListLayout.TILES -> StopTiles(
                    plan = plan,
                    selection = selection,
                    onPickPoint = onPickPoint,
                    onOpenStop = onOpenStop,
                    departure = departure,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Same buttons in both layouts, so they stay put while the content slides.
        actions()
    }
}

/** The charge-level sheets behind the start and destination rows; confirming either re-plans. */
@Composable
private fun SocEditors(socEditing: SocEditing) {
    socEditing.socInput?.let { input ->
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

    socEditing.arrivalSocInput?.let { input ->
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
}


/** Start, stops and destination as rows, with the drive between two of them written in between. */
@Composable
private fun StopCards(
    plan: TripPlan,
    startSocPercent: Double?,
    selection: SectionSelection,
    onPickPoint: (Int) -> Unit,
    onOpenStop: (PlannedStop) -> Unit,
    socEditing: SocEditing?,
    departure: LocalTime,
    modifier: Modifier = Modifier,
) {
    val selecting = selection.selecting
    val last = plan.stops.size + 1
    val legs = plan.legs
    val context = LocalContext.current
    fun picked(index: Int) = selecting && selection.includes(index)

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        item(key = "start") {
            EndRow(
                title = plan.startAddress ?: stringResource(Res.string.trip_start),
                time = etaText(context, 0.0, departure),
                level = startSocPercent,
                levelUnknown = stringResource(Res.string.trip_dep_now_unknown),
                selected = picked(0),
                onClick = if (selecting) ({ onPickPoint(0) }) else socEditing?.onEditStartSoc,
                editDescription = socEditing?.let { stringResource(Res.string.trip_soc_edit) },
            )
        }
        itemsIndexed(plan.stops, key = { _, stop -> stop.site.id }) { i, stop ->
            val index = i + 1
            Column {
                LegRow(legs[i], inSection = picked(i) && picked(index))
                StopCard(
                    index = index,
                    stop = stop,
                    arrival = etaText(context, stop.arrivalMinutesFromStart, departure),
                    selected = picked(index),
                    onClick = { if (selecting) onPickPoint(index) else onOpenStop(stop) },
                )
            }
        }
        item(key = "destination") {
            Column {
                LegRow(legs.last(), inSection = picked(last - 1) && picked(last))
                EndRow(
                    title = plan.destination.name,
                    time = etaText(context, plan.totalMinutes, departure),
                    level = plan.arrivalSocPercent,
                    selected = picked(last),
                    onClick = if (selecting) ({ onPickPoint(last) }) else socEditing?.onEditArrivalSoc,
                    editDescription = socEditing?.let { stringResource(Res.string.trip_arrival_soc_edit) },
                )
            }
        }
    }
}

/** The trip's start or destination: a pin, the name and one time, the charge level below. */
@Composable
private fun EndRow(
    title: String,
    onClick: (() -> Unit)?,
    /** Names what a tap edits and shows the pen; `null` on a row that edits nothing. */
    editDescription: String?,
    time: String? = null,
    level: Double? = null,
    /** Stands in for an unknown [level]; without either the row has no second line. */
    levelUnknown: String? = null,
    selected: Boolean = false,
) {
    val hasLevelLine = level != null || levelUnknown != null
    Column(
        verticalArrangement = Arrangement.spacedBy(LINE_GAP),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = CARD_PADDING, vertical = ROW_PADDING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MARKER_SIZE),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = MARKER_GAP).weight(1f),
            )
            if (!hasLevelLine) EditPen(editDescription)
            time?.let { ClockText(it) }
        }
        if (hasLevelLine) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = SECOND_LINE_INSET),
            ) {
                if (level != null) {
                    BatteryIcon(level)
                    LevelText(level)
                } else if (levelUnknown != null) {
                    MetaLine(levelUnknown)
                }
                EditPen(editDescription)
            }
        }
    }
}

@Composable
private fun StopCard(index: Int, stop: PlannedStop, arrival: String, selected: Boolean, onClick: () -> Unit) {
    AppCard(onClick = onClick, selected = selected, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LINE_GAP),
            modifier = Modifier.padding(horizontal = CARD_PADDING, vertical = ROW_PADDING),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StopBadge(index)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = MARKER_GAP).weight(1f)) {
                    Text(
                        stop.site.operator ?: stop.site.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    stop.site.address?.town?.let { town ->
                        Text(
                            town,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                ClockText(arrival)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = SECOND_LINE_INSET),
            ) {
                BatteryIcon(stop.arrivalSocPercent)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    LevelText(stop.arrivalSocPercent)
                    Icon(Icons.AutoMirrored.Filled.ArrowRightAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    LevelText(stop.departureSocPercent)
                }
                MetaLine("·")
                MetaLine(stringResource(Res.string.garage_kw, stop.maxPowerKw.roundToInt()))
                Spacer(Modifier.weight(1f))
                MetaLine(minutesText(stop.chargeMinutes))
            }
        }
    }
}

/** The drive to the row below: a dotted line under the markers, time and distance beside it. */
@Composable
private fun LegRow(leg: TripLeg, inSection: Boolean) {
    val dots = if (inSection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Text(
        listOf(
            minutesText(leg.driveMinutes),
            stringResource(Res.string.trip_summary_distance, leg.distanceKm.roundToInt()),
        ).joinToString(" · "),
        style = MaterialTheme.typography.labelSmall.tabular,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = (CARD_PADDING + MARKER_SIZE / 2).toPx()
                val dot = 2.dp.toPx()
                drawLine(
                    color = dots,
                    start = Offset(x, dot),
                    end = Offset(x, size.height - dot),
                    strokeWidth = dot,
                    cap = StrokeCap.Round,
                    // Zero-length dashes with round caps come out as dots.
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, 2.5f * dot)),
                )
            }
            .padding(start = CARD_PADDING + SECOND_LINE_INSET, top = 6.dp, bottom = 6.dp),
    )
}

@Composable
private fun StopBadge(index: Int) {
    RankBadge(index, MaterialTheme.colorScheme.primary, textColor = MaterialTheme.colorScheme.onPrimary)
}

@Composable
private fun ClockText(time: String) {
    Text(time, style = MaterialTheme.typography.titleSmall.tabular, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
}

@Composable
private fun LevelText(percent: Double) {
    Text(stringResource(Res.string.garage_percent, percent.roundToInt()), style = MaterialTheme.typography.labelMedium.tabular, maxLines = 1)
}

@Composable
private fun EditPen(description: String?) {
    if (description == null) return
    Icon(
        painterResource(R.drawable.ic_pen),
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(14.dp),
    )
}

/** A horizontal battery, filled to [percent]. */
@Composable
private fun BatteryIcon(percent: Double) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(width = 20.dp, height = 11.dp)) {
        val stroke = 1.5.dp.toPx()
        val nub = 2.dp.toPx()
        val bodyWidth = size.width - nub
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyWidth - stroke, size.height - stroke),
            cornerRadius = CornerRadius(2.5.dp.toPx()),
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(bodyWidth, size.height * 0.3f),
            size = Size(nub, size.height * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        val inset = stroke + 1.5.dp.toPx()
        val level = (percent / 100).coerceIn(0.0, 1.0).toFloat()
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size((bodyWidth - 2 * inset) * level, size.height - 2 * inset),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

@Composable
private fun MetaLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

/** The stops as a swipeable row for the collapsed sheet. */
@Composable
private fun StopTiles(
    plan: TripPlan,
    selection: SectionSelection,
    onPickPoint: (Int) -> Unit,
    onOpenStop: (PlannedStop) -> Unit,
    departure: LocalTime,
    modifier: Modifier = Modifier,
) {
    val selecting = selection.selecting
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
                        StopBadge(index)
                        Text(
                            stop.site.operator ?: stop.site.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MetaLine(stringResource(Res.string.trip_tile_charge, stop.maxPowerKw.roundToInt(), stop.chargeMinutes.roundToInt()))
                        MetaLine(stringResource(Res.string.trip_tile_arrival, etaText(context, stop.arrivalMinutesFromStart, departure)))
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
            ReplanChip(onReplan)
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

@Composable
private fun ReplanChip(onReplan: () -> Unit) {
    AssistChip(
        onClick = onReplan,
        label = { Text(stringResource(Res.string.trip_replan)) },
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_route), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
        },
        shape = MaterialTheme.shapes.small,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = null,
    )
}

/** The summary row when no plan came out: the reason in short, and the way to try again. */
@Composable
fun UnreachableTripSummary(onReplan: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                // The planned summary's height, which its layout button sets.
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .padding(start = 16.dp, end = 10.dp, top = 2.dp, bottom = 2.dp)
                .fillMaxWidth(),
        ) {
            Text(
                stringResource(Res.string.trip_unreachable_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.weight(1f))
            ReplanChip(onReplan)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Start and destination with the reason in between, both levels still editable:
 * the way out of "no charger in reach" is a different level.
 */
@Composable
fun UnreachableTripContent(trip: TripUiState.Unreachable, socEditing: SocEditing, modifier: Modifier = Modifier) {
    SocEditors(socEditing)
    // The sheet stays at its peek here, so a large font scrolls instead of cutting the destination off.
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
        EndRow(
            title = stringResource(Res.string.trip_start),
            level = trip.startSocPercent,
            levelUnknown = stringResource(Res.string.trip_dep_now_unknown),
            onClick = socEditing.onEditStartSoc,
            editDescription = stringResource(Res.string.trip_soc_edit),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = CARD_PADDING, vertical = 6.dp),
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(MARKER_SIZE),
            )
            Text(
                trip.why.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = MARKER_GAP),
            )
        }
        EndRow(
            title = trip.destination.name,
            onClick = socEditing.onEditArrivalSoc,
            editDescription = stringResource(Res.string.trip_arrival_soc_edit),
        )
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

fun CommittedTrip.departureTime(): LocalTime =
    Instant.ofEpochMilli(committedAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalTime()

/** Wall-clock arrival, from [now] plus the ETA offset, in the device's 12h/24h style. */
fun etaText(context: Context, minutesFromStart: Double, now: LocalTime): String {
    val locale = context.resources.configuration.locales[0].forUi()
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

private val MARKER_SIZE = 24.dp
private val MARKER_GAP = 10.dp
private val CARD_PADDING = 12.dp
private val ROW_PADDING = 9.dp
private val LINE_GAP = 3.dp

/** Puts a row's second line under its title, past the marker. */
private val SECOND_LINE_INSET = MARKER_SIZE + MARKER_GAP
