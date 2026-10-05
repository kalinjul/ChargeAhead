package org.julakali.chargeahead.android.phone

import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.components.ChargeLevelKind
import org.julakali.chargeahead.android.phone.components.ChargeLevelSheet
import org.julakali.chargeahead.android.phone.components.SectionLabel
import org.julakali.chargeahead.android.phone.components.SettingRow
import org.julakali.chargeahead.android.phone.components.SettingsCard
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.ui.GarageUiState
import org.julakali.chargeahead.shared.ui.GarageViewModel
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun GarageRoute(
    onOpenVehicle: () -> Unit,
    onOpenAdd: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarageViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GarageScreen(
        uiState = uiState,
        onSelect = viewModel::onVehicleSelected,
        onOpenVehicle = onOpenVehicle,
        onOpenAdd = onOpenAdd,
        onArrivalSheetOpen = viewModel::onArrivalSheetOpened,
        onArrivalChange = viewModel::onArrivalSheetChanged,
        onArrivalConfirm = viewModel::onArrivalSheetConfirmed,
        onArrivalDismiss = viewModel::onArrivalSheetDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    uiState: GarageUiState,
    onSelect: (VehicleProfile) -> Unit,
    onOpenVehicle: () -> Unit,
    onOpenAdd: () -> Unit,
    onArrivalSheetOpen: () -> Unit,
    onArrivalChange: (Int) -> Unit,
    onArrivalConfirm: () -> Unit,
    onArrivalDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = uiState.selected

    uiState.arrivalSheet?.let { percent ->
        ChargeLevelSheet(
            title = stringResource(R.string.garage_arrival_title),
            subtitle = stringResource(R.string.garage_arrival_sheet_hint),
            kind = ChargeLevelKind.ARRIVAL,
            percent = percent,
            onChange = onArrivalChange,
            onConfirm = onArrivalConfirm,
            onDismiss = onArrivalDismiss,
        )
    }

    Box(modifier) {
    Column(
        // No side padding: the cards run out to the screen edge.
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = if (selected != null) FAB_CLEARANCE else 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val inset = Modifier.padding(horizontal = PAGE_INSET)
        if (selected == null) {
            EmptyGarageCard(onOpenAdd, inset)
        } else {
            // A fresh pager when the cars change: a removal would leave it between two pages.
            key(uiState.vehicles.map { it.id }) {
                CarPager(
                    vehicles = uiState.vehicles,
                    selected = selected,
                    fullRangeKm = uiState.fullRangeKm,
                    onSelect = onSelect,
                    modifier = Modifier.padding(bottom = CARDS_BREAK),
                )
            }
        }
        val arrivalRow: @Composable () -> Unit = {
            SettingRow(
                icon = Icons.Outlined.Flag,
                title = stringResource(R.string.garage_arrival_title),
                supporting = stringResource(R.string.garage_arrival_row_hint),
                value = stringResource(R.string.garage_percent, uiState.arrivalSocPercent.roundToInt()),
                onClick = onArrivalSheetOpen,
            )
        }
        val vehicleRow: @Composable () -> Unit = {
            SettingRow(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.garage_vehicle_row),
                supporting = stringResource(R.string.garage_vehicle_row_hint),
                onClick = onOpenVehicle,
            )
        }
        SettingsCard(if (selected == null) listOf(arrivalRow) else listOf(vehicleRow, arrivalRow), inset)
    }
    if (selected != null) {
        val label = stringResource(R.string.garage_add_title)
        ExtendedFloatingActionButton(
            onClick = onOpenAdd,
            // Material hides the text from TalkBack; the label goes on the icon.
            icon = { Icon(Icons.Outlined.Add, contentDescription = label) },
            text = { Text(label) },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        )
    }
    }
}

@Composable
private fun CarPager(
    vehicles: List<VehicleProfile>,
    selected: VehicleProfile,
    fullRangeKm: Map<String, Double>,
    onSelect: (VehicleProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = vehicles.size
    val selectedIndex = vehicles.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
    // Endless: page p shows car p % count, starting mid-range so both ways stay open.
    val endless = count > 1
    val startPage = remember { if (endless) ENDLESS_MIDDLE - ENDLESS_MIDDLE % count + selectedIndex else 0 }
    val pagerState = rememberPagerState(initialPage = startPage) { if (endless) ENDLESS_PAGES else count }
    val currentSelected by rememberUpdatedState(selected)
    val currentVehicles by rememberUpdatedState(vehicles)
    val currentOnSelect by rememberUpdatedState(onSelect)

    LaunchedEffect(selectedIndex) {
        val settled = pagerState.settledPage
        if (settled.mod(count) != selectedIndex) pagerState.animateScrollToPage(settled.nearestPageOf(selectedIndex, count))
    }
    // Only where a swipe settles: pages passed on the way select nothing.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentVehicles.getOrNull(page.mod(currentVehicles.size))?.takeIf { it.id != currentSelected.id }?.let(currentOnSelect)
        }
    }

    HorizontalPager(
        state = pagerState,
        // No indicator, as in Material's carousels: the stacked card's end says there is more.
        contentPadding = PaddingValues(start = PAGE_INSET, end = if (vehicles.size > 1) PAGE_INSET + STACK_PEEK else PAGE_INSET),
        pageSpacing = CARD_GAP,
        // Composed ahead, so the card after next can rise out of the stack mid-swipe.
        beyondViewportPageCount = 2,
        modifier = modifier,
    ) { page ->
        val vehicle = vehicles[page.mod(count)]
        val ground = MaterialTheme.colorScheme.background
        CarCard(
            vehicle = vehicle,
            fullRangeKm = fullRangeKm[vehicle.id],
            shadow = { stackPlacement(pagerState.getOffsetDistanceInPages(page), 0f, 0f, 0f).shadow },
            // Earlier cards on top: a swipe lifts the top card off the stack. Relative to the start
            // page, because indices near a billion lose Float precision.
            modifier = Modifier
                .zIndex((startPage - page).toFloat())
                // TalkBack reads only the card on top.
                .then(if (page == pagerState.currentPage) Modifier else Modifier.clearAndSetSemantics {})
                // ModulateAlpha: an offscreen buffer would cut the shadow off at the card's edge.
                .graphicsLayer {
                    val place = stackPlacement(pagerState.getOffsetDistanceInPages(page), size.width, CARD_GAP.toPx(), STACK_PEEK.toPx())
                    scaleX = place.scale
                    scaleY = place.scale
                    translationX = place.translationX
                    alpha = place.alpha
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                // Dimmed with a veil, not alpha: a see-through card would show the one beneath.
                .drawWithContent {
                    drawContent()
                    val veil = stackPlacement(pagerState.getOffsetDistanceInPages(page), size.width, CARD_GAP.toPx(), STACK_PEEK.toPx()).veil
                    if (veil > 0f) drawRoundRect(color = ground.copy(alpha = veil), cornerRadius = CornerRadius(CARD_CORNER.toPx()))
                },
        )
    }
}

@Composable
private fun CarCard(
    vehicle: VehicleProfile,
    fullRangeKm: Double?,
    modifier: Modifier = Modifier,
    shadow: () -> Float = { 1f },
) {
    val shape = RoundedCornerShape(CARD_CORNER)
    Box(modifier.fillMaxWidth()) {
        // Drawn and kept outside the outline: an elevation shadow shows through a fading card.
        Spacer(
            Modifier
                .matchParentSize()
                .drawWithCache {
                    val outline = Path().apply { addRoundRect(RoundRect(size.toRect(), CornerRadius(CARD_CORNER.toPx()))) }
                    onDrawWithContent { clipPath(outline, ClipOp.Difference) { this@onDrawWithContent.drawContent() } }
                }
                .dropShadow(shape) {
                    radius = SHADOW_BLUR.toPx()
                    offset = Offset(0f, SHADOW_DROP.toPx())
                    color = Color.Black
                    alpha = SHADOW_ALPHA * shadow()
                },
        )
        CarCardFace(vehicle, fullRangeKm, shape)
    }
}

@Composable
private fun CarCardFace(vehicle: VehicleProfile, fullRangeKm: Double?, shape: Shape) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(vehicle.displayName, style = MaterialTheme.typography.titleLarge)
            fullRangeKm?.let { km ->
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        stringResource(R.string.garage_range_number, km.roundToInt()),
                        style = MaterialTheme.typography.displayMedium.tabular,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        " " + stringResource(R.string.garage_range_unit),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    stringResource(R.string.garage_range_caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
                )
            }
            HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 16.dp))
            // Number above, unit below, so the three columns line up however long the unit is.
            Row {
                Stat(
                    stringResource(R.string.garage_stat_battery),
                    vehicle.usableBatteryKwh.oneDecimal(),
                    stringResource(R.string.garage_unit_kwh),
                    Modifier.weight(1f),
                )
                Stat(
                    stringResource(R.string.garage_stat_dc),
                    vehicle.dcPeakPowerKw?.roundToInt()?.toString() ?: "–",
                    stringResource(R.string.garage_unit_kw),
                    Modifier.weight(1f),
                )
                Stat(
                    stringResource(R.string.garage_stat_consumption),
                    vehicle.consumptionKwhPer100Km.oneDecimal(),
                    stringResource(R.string.garage_unit_kwh_per_100),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA))
        Text(value, style = MaterialTheme.typography.titleLarge.tabular, modifier = Modifier.padding(top = 2.dp))
        Text(unit, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA))
    }
}

@Composable
private fun EmptyGarageCard(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DirectionsCar, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
            Text(stringResource(R.string.garage_empty_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                stringResource(R.string.garage_empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Button(onClick = onAdd, modifier = Modifier.padding(top = 20.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.garage_add_title))
            }
        }
    }
}

/** More than anyone swipes, small enough to stay clear of Int overflow. */
private const val ENDLESS_PAGES = 1_000_000
private const val ENDLESS_MIDDLE = ENDLESS_PAGES / 2

private fun Int.nearestPageOf(index: Int, count: Int): Int {
    val delta = (index - mod(count)).mod(count)
    return if (delta <= count / 2) this + delta else this + delta - count
}

private val CARD_CORNER = 24.dp

/** About what 3dp of elevation looks like. */
private val SHADOW_BLUR = 8.dp
private val SHADOW_DROP = 2.dp
private const val SHADOW_ALPHA = 0.18f

private val STACK_PEEK = 16.dp

private val CARD_GAP = 8.dp

private const val NEIGHBOUR_SCALE = 0.85f

private const val NEIGHBOUR_ALPHA = 0.8f

private const val DEEP_SCALE = 0.75f

/** On top of the usual 16dp, which felt cramped below the cards. */
private val CARDS_BREAK = 16.dp

/** So the floating button never covers the last row. */
private val FAB_CLEARANCE = 88.dp

private val PAGE_INSET = 18.dp

private const val SECONDARY_ALPHA = 0.75f

data class StackPlacement(val scale: Float, val translationX: Float, val alpha: Float, val veil: Float, val shadow: Float)

fun stackPlacement(offset: Float, cardWidth: Float, gap: Float, peek: Float): StackPlacement {
    if (offset < 0f) {
        val left = (1f + offset).coerceAtLeast(0f)
        return StackPlacement(scale = 1f, translationX = 0f, alpha = left, veil = 0f, shadow = left)
    }
    val first = offset.coerceAtMost(1f)
    val deeper = offset - first
    val placeScale = 1f - (1f - NEIGHBOUR_SCALE) * first
    val tuck = cardWidth * (1f + placeScale) / 2f + gap - peek
    return StackPlacement(
        scale = placeScale - (NEIGHBOUR_SCALE - DEEP_SCALE) * deeper.coerceAtMost(1f),
        translationX = -first * tuck - deeper * (cardWidth + gap),
        alpha = if (offset >= 2f) 0f else 1f,
        veil = (1f - NEIGHBOUR_ALPHA) * first + NEIGHBOUR_ALPHA * deeper.coerceAtMost(1f),
        shadow = if (offset >= 2f) 0f else 1f,
    )
}
