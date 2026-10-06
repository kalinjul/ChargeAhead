package org.julakali.chargeahead.android.phone

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.testTag
import org.julakali.chargeahead.android.phone.theme.ChargeAheadColors
import org.julakali.chargeahead.shared.domain.ChargeMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.julakali.chargeahead.android.phone.theme.ChargeAheadMotion
import org.julakali.chargeahead.android.phone.theme.ChargeAheadTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import kotlinx.coroutines.launch
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.MapCharger
import org.julakali.chargeahead.shared.ui.HomeUiState
import org.julakali.chargeahead.shared.ui.HomeViewModel
import org.julakali.chargeahead.shared.ui.SearchRow
import org.julakali.chargeahead.shared.ui.SearchUiState
import org.julakali.chargeahead.shared.ui.SearchViewModel
import org.koin.androidx.compose.koinViewModel
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.home_mode_off
import org.julakali.chargeahead.shared.resources.home_pill_active_route
import org.julakali.chargeahead.shared.resources.home_pill_charge_now
import org.julakali.chargeahead.shared.resources.home_settings
import org.julakali.chargeahead.shared.resources.map_my_location
import org.julakali.chargeahead.shared.resources.mode_ac
import org.julakali.chargeahead.shared.resources.mode_browse
import org.julakali.chargeahead.shared.resources.phone_permission_action
import org.julakali.chargeahead.shared.resources.phone_permission_message
import org.julakali.chargeahead.shared.resources.phone_status_location_unavailable
import org.julakali.chargeahead.shared.resources.plan_planning

private enum class HomeMode { BROWSING, SEARCHING, TRIP }

/** The map screen with its state holder attached. */
@Composable
fun HomeRoute(
    /** `null` until the platform has been asked. */
    hasPermission: Boolean?,
    planningInProgress: Boolean,
    /** The planned trip: its route replaces the browsing markers, its destination the search bar. */
    trip: TripPlan?,
    /** The trip sheet's peek, so the route fits above it. */
    mapBottomInset: Dp,
    onRequestPermission: () -> Unit,
    /** The location button with nothing to center on. Owned by the activity. */
    onLocate: () -> Unit,
    onSettings: () -> Unit,
    onChargeNow: () -> Unit,
    /** The committed trip's page; the pill is dimmed while there is none. */
    activeRouteEnabled: Boolean,
    onActiveRoute: () -> Unit,
    /** A numbered route marker was tapped, 1-based. */
    onStopTapped: (Int) -> Unit,
    /** Arrival and departure for a selected site that is a planned stop. */
    tripLineFor: (ChargeStop) -> String?,
    /** A search hit was picked; the search has closed by then. */
    onPick: (Destination) -> Unit,
    onClearTrip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    searchViewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchUi by searchViewModel.uiState.collectAsStateWithLifecycle()

    // The pipeline may only run once the permission is there.
    LaunchedEffect(hasPermission) {
        if (hasPermission == true) viewModel.onLocationPermissionGranted()
    }

    HomeScreen(
        uiState = uiState,
        search = searchUi,
        trip = trip,
        hasPermission = hasPermission,
        planningInProgress = planningInProgress,
        mapBottomInset = mapBottomInset,
        onViewportChanged = viewModel::onViewportChanged,
        onChargerTapped = viewModel::onChargerSelected,
        onRequestPermission = onRequestPermission,
        onLocate = onLocate,
        onSettings = onSettings,
        onChargeNow = onChargeNow,
        activeRouteEnabled = activeRouteEnabled,
        onActiveRoute = onActiveRoute,
        onModeDismiss = viewModel::onModeDismissed,
        onStopTapped = onStopTapped,
        onSearchExpandedChange = { open -> if (open) searchViewModel.onOpened() else searchViewModel.onClosed() },
        onQueryChange = searchViewModel::onQueryChanged,
        onPick = { row ->
            searchViewModel.onClosed()
            onPick(row.destination)
        },
        onClearTrip = onClearTrip,
        modifier = modifier,
    )

    uiState.selectedStop?.let { stop ->
        ChargeStopDetailSheet(
            stop = stop,
            live = uiState.selectedStopLive,
            onDismiss = viewModel::onSelectedStopDismissed,
            tripLine = tripLineFor(stop),
        )
    }
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    search: SearchUiState,
    trip: TripPlan?,
    hasPermission: Boolean?,
    planningInProgress: Boolean,
    mapBottomInset: Dp,
    onViewportChanged: (BoundingBox?) -> Unit,
    onChargerTapped: (MapCharger) -> Unit,
    onRequestPermission: () -> Unit,
    onLocate: () -> Unit,
    onSettings: () -> Unit,
    onChargeNow: () -> Unit,
    activeRouteEnabled: Boolean,
    onActiveRoute: () -> Unit,
    /** The mode pill was tapped: AC mode or Stöbermodus goes off. */
    onModeDismiss: () -> Unit,
    onStopTapped: (Int) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onPick: (SearchRow) -> Unit,
    onClearTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeMode = when {
        search.expanded -> HomeMode.SEARCHING
        trip != null -> HomeMode.TRIP
        else -> HomeMode.BROWSING
    }
    val route = remember(trip) { trip?.toRouteOverlay() }
    val camera = rememberHomeCamera(uiState.position)
    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        if (LocalInspectionMode.current) {
            // Previews have no Maps SDK; a plain backdrop keeps them the same with or without a key.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
        } else if (hasGoogleMapsKey) {
            HomeGoogleMap(
                position = uiState.position,
                // The route replaces the browsing markers.
                chargers = if (homeMode == HomeMode.TRIP) emptyList() else uiState.chargers,
                route = route,
                bottomInset = mapBottomInset,
                hasLocationPermission = hasPermission == true,
                cameraPositionState = camera,
                onViewportChanged = onViewportChanged,
                onChargerTapped = onChargerTapped,
                onStopTapped = onStopTapped,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MissingMapsKeyNotice(Modifier.fillMaxSize())
        }

        // A mode that is on tints the screen's edges, so it can't stay on unnoticed.
        ModeGlow(uiState.mode)
        if (homeMode != HomeMode.SEARCHING) {
            ModeFlag(uiState.mode, onDismiss = onModeDismiss, modifier = Modifier.align(Alignment.CenterEnd))
        }

        // While the panel is open the map only takes a dismissing tap.
        if (homeMode == HomeMode.SEARCHING) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onSearchExpandedChange(false) } },
            )
        }

        // Burger, bar, locate on one line. While searching the sides fold away and
        // Material's docked bar takes the whole width; compass and spinner hang on the right.
        // The opened bar grows downward, so nothing here may be placed relative to its height.
        val searching = homeMode == HomeMode.SEARCHING
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(SCREEN_MARGIN)
                .fillMaxWidth(),
        ) {
            // Drawn first, so the opened panel covers it.
            Row(Modifier.fillMaxWidth().padding(top = SEARCH_BAR_HEIGHT + 8.dp)) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        searching -> Unit
                        // Location is running and getting nowhere.
                        uiState.locationUnavailable -> HintChip(
                            stringResource(Res.string.phone_status_location_unavailable),
                            MaterialTheme.colorScheme.error,
                        )
                    }
                }
                SideButton(visible = !searching, edge = ScreenEdge.END) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(ROUND_BUTTON_SIZE),
                    ) {
                        val bearing = camera.position.bearing
                        if (bearing != 0f) {
                            CompassButton(bearing = { camera.position.bearing }) {
                                scope.launch {
                                    camera.animate(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder(camera.position).bearing(0f).tilt(0f).build(),
                                        ),
                                    )
                                }
                            }
                        }
                        // A charger source is being asked over the network.
                        if (uiState.loadingSites) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Top) {
                SideButton(visible = !searching, edge = ScreenEdge.START, modifier = Modifier.height(SEARCH_BAR_HEIGHT)) {
                    RoundIconButton(onClick = onSettings) {
                        Icon(
                            Icons.Outlined.Menu,
                            contentDescription = stringResource(Res.string.home_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                HomeTopBar(
                    search = search,
                    trip = trip,
                    onExpandedChange = onSearchExpandedChange,
                    onQueryChange = onQueryChange,
                    onPick = onPick,
                    onClearTrip = onClearTrip,
                    onFlyTo = { target -> scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), HOME_ZOOM)) } },
                    modifier = Modifier.weight(1f),
                )
                SideButton(visible = !searching, edge = ScreenEdge.END, modifier = Modifier.height(SEARCH_BAR_HEIGHT)) {
                    RoundIconButton(onClick = {
                        // With a position, center on it; otherwise ask for a fix.
                        val target = uiState.position
                        if (target == null) {
                            onLocate()
                        } else {
                            scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), LOCATE_ZOOM)) }
                        }
                    }) {
                        if (uiState.searchingLocation) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = stringResource(Res.string.map_my_location),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        if (hasPermission == false) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(Res.string.phone_permission_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRequestPermission) {
                        Text(stringResource(Res.string.phone_permission_action))
                    }
                }
            }
        }

        if (planningInProgress) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(Res.string.plan_planning))
                }
            }
        }

        if (homeMode == HomeMode.BROWSING) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            ) {
                if (uiState.belowMinZoom) {
                    // Tapping the hint lands on full markers, not on the dot tier.
                    ZoomHintChip { scope.launch { camera.animate(CameraUpdateFactory.zoomTo(PILL_ZOOM)) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HomePill(
                        text = stringResource(Res.string.home_pill_charge_now),
                        icon = painterResource(R.drawable.ic_battery),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = onChargeNow,
                    )
                    HomePill(
                        text = stringResource(Res.string.home_pill_active_route),
                        icon = painterResource(R.drawable.ic_route),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        iconTint = MaterialTheme.colorScheme.primary,
                        enabled = activeRouteEnabled,
                        onClick = onActiveRoute,
                    )
                }
            }
        }
    }
}

/**
 * The mode's colour bleeding in from the screen's edges, breathing slowly; nothing for
 * [ChargeMode.NORMAL]. Compose's own inner shadow does the blur. Takes no touches.
 */
@Composable
private fun ModeGlow(mode: ChargeMode) {
    // Crossfade, so the fading-out glow keeps the colour of the mode that just went off,
    // and nothing animates the size: AnimatedContent would clip the exit to a shrinking corner.
    Crossfade(targetState = mode, label = "modeGlow") { shown ->
        // Inside the branch: with no mode on there is nothing to breathe, and an infinite
        // transition out here would keep asking for frames while the app sits idle on the map.
        if (shown != ChargeMode.NORMAL) {
            val breath by rememberInfiniteTransition(label = "modeBreath").animateFloat(
                initialValue = GLOW_BREATH_FLOOR,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(GLOW_BREATH_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "modeBreathAlpha",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("modeGlow")
                    .graphicsLayer { alpha = breath }
                    .innerShadow(
                        RectangleShape,
                        Shadow(radius = GLOW_REACH, color = ChargeAheadColors.forMode(shown), alpha = GLOW_ALPHA),
                    ),
            )
        }
    }
}

/** A small flag on the right edge, mid-height, naming the mode that is on; tapping it switches the mode off. */
@Composable
private fun ModeFlag(mode: ChargeMode, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (mode == ChargeMode.NORMAL) return
    val color = ChargeAheadColors.forMode(mode)
    Surface(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.small.copy(topEnd = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        color = color,
        contentColor = ChargeAheadColors.onMode(mode),
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                stringResource(if (mode == ChargeMode.AC) Res.string.mode_ac else Res.string.mode_browse),
                style = MaterialTheme.typography.labelMedium,
            )
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.home_mode_off),
                modifier = Modifier.padding(start = 4.dp).size(14.dp),
            )
        }
    }
}

/** The inner shadow's blur radius and opacity, and how slowly the glow breathes. */
private val GLOW_REACH = 48.dp
private const val GLOW_ALPHA = 0.75f
private const val GLOW_BREATH_MILLIS = 3600

/** How dim the glow gets at the bottom of a breath. */
private const val GLOW_BREATH_FLOOR = 0.75f

private fun TripPlan.toRouteOverlay() = RouteOverlay(
    points = route.points,
    stops = stops.mapIndexed { index, stop -> RouteStop(index + 1, stop.site.position, operatorColor(stop.site)) },
    destination = destination.position,
)

private enum class ScreenEdge { START, END }

/** A button beside the bar that slides off its screen edge while the bar grows into its place. */
@Composable
private fun SideButton(
    visible: Boolean,
    edge: ScreenEdge,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // The slot shrinks unclipped while the button slides, so it clears the screen margin instead of being cut off.
    val margin = with(LocalDensity.current) { SCREEN_MARGIN.roundToPx() }
    val towards = if (edge == ScreenEdge.START) Alignment.Start else Alignment.End
    val offScreen: (Int) -> Int = { width -> if (edge == ScreenEdge.START) -(width + margin) else width + margin }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(ChargeAheadMotion.effects()) +
            slideInHorizontally(ChargeAheadMotion.spatial(), offScreen) +
            expandHorizontally(ChargeAheadMotion.spatial(), expandFrom = towards, clip = false),
        exit = fadeOut(ChargeAheadMotion.effects()) +
            slideOutHorizontally(ChargeAheadMotion.spatial(), offScreen) +
            shrinkHorizontally(ChargeAheadMotion.spatial(), shrinkTowards = towards, clip = false),
    ) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            if (edge == ScreenEdge.END) Spacer(Modifier.width(SIDE_BUTTON_GAP))
            content()
            if (edge == ScreenEdge.START) Spacer(Modifier.width(SIDE_BUTTON_GAP))
        }
    }
}

internal val SCREEN_MARGIN = 16.dp

private val SIDE_BUTTON_GAP = 10.dp

/** From the screen edge to the closed search bar, past the round button beside it. */
internal val SEARCH_BAR_INSET = SCREEN_MARGIN + ROUND_BUTTON_SIZE + SIDE_BUTTON_GAP

@Composable
private fun HomePreview(
    uiState: HomeUiState = HomeUiState(position = LatLon(53.5511, 9.9937)),
    hasPermission: Boolean? = true,
    planningInProgress: Boolean = false,
    activeRouteEnabled: Boolean = true,
) {
    ChargeAheadTheme {
        HomeScreen(
            uiState = uiState,
            search = SearchUiState(),
            trip = null,
            hasPermission = hasPermission,
            planningInProgress = planningInProgress,
            mapBottomInset = 0.dp,
            onViewportChanged = {},
            onChargerTapped = {},
            onRequestPermission = {},
            onLocate = {},
            onSettings = {},
            onChargeNow = {},
            activeRouteEnabled = activeRouteEnabled,
            onActiveRoute = {},
            onModeDismiss = {},
            onStopTapped = {},
            onSearchExpandedChange = {},
            onQueryChange = {},
            onPick = {},
            onClearTrip = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun HomeBrowsingPreview() = HomePreview()

@Preview(widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeBrowsingDarkPreview() = HomePreview()

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun HomeZoomedOutLoadingPreview() = HomePreview(
    uiState = HomeUiState(position = LatLon(53.5511, 9.9937), belowMinZoom = true, loadingSites = true),
    activeRouteEnabled = false,
)

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun HomeLocationUnavailablePreview() = HomePreview(uiState = HomeUiState(searchingLocation = true, locationUnavailable = true))

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun HomeWithoutPermissionPreview() = HomePreview(uiState = HomeUiState(), hasPermission = false)

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun HomePlanningPreview() = HomePreview(planningInProgress = true)
