package org.julakali.chargeahead.android.phone

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.theme.ChargeAheadMotion
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.domain.MapsHandoff
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.ui.DrawerUiState
import org.julakali.chargeahead.shared.ui.DrawerViewModel
import org.julakali.chargeahead.shared.ui.HomeViewModel
import org.julakali.chargeahead.shared.ui.SearchRow
import org.julakali.chargeahead.shared.ui.SearchViewModel
import org.julakali.chargeahead.shared.ui.PhoneAppViewModel
import org.julakali.chargeahead.shared.ui.TripEvent
import org.julakali.chargeahead.shared.ui.TripUiState
import org.julakali.chargeahead.shared.ui.CommittedTripViewModel
import org.julakali.chargeahead.shared.ui.TripViewModel
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * The phone app: wires the map screen and the navigator's destinations
 * together. [librariesRes] is the AboutLibraries JSON, generated in the
 * app module that owns all dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneApp(librariesRes: Int) {
    val phoneAppViewModel: PhoneAppViewModel = koinViewModel()
    val drawerViewModel: DrawerViewModel = koinViewModel()
    val homeViewModel: HomeViewModel = koinViewModel()
    val tripViewModel: TripViewModel = koinViewModel()
    val searchViewModel: SearchViewModel = koinViewModel()
    val committedTripViewModel: CommittedTripViewModel = koinViewModel()

    val phoneAppUi by phoneAppViewModel.uiState.collectAsStateWithLifecycle()
    val drawerUi by drawerViewModel.uiState.collectAsStateWithLifecycle()
    val tripUi by tripViewModel.uiState.collectAsStateWithLifecycle()
    val searchUi by searchViewModel.uiState.collectAsStateWithLifecycle()
    val committedUi by committedTripViewModel.uiState.collectAsStateWithLifecycle()
    val planned = tripUi as? TripUiState.Planned
    // Kept past "Navigieren beenden", so the page keeps its name while it slides out.
    var activeRouteTitle by remember { mutableStateOf<String?>(null) }
    committedUi.trip?.plan?.destination?.name?.let { activeRouteTitle = it }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val focusManager = LocalFocusManager.current
    val clock = LocalNow.current
    val navigator = rememberPhoneNavigator()

    fun closeSearch() {
        phoneAppViewModel.onSearchClosed()
        focusManager.clearFocus()
        searchViewModel.onClosed()
    }

    fun collapseTripSheet() {
        scope.launch { sheetState.partialExpand() }
    }

    // No vehicle? Planning reports it as an event.
    fun pickRow(row: SearchRow) {
        closeSearch()
        tripViewModel.plan(row.destination)
    }

    fun sendToMaps(link: String) {
        if (context.openMapsLink(link)) snackbar.show(scope, context.getString(R.string.trip_maps_sent))
    }

    LocationHandshakeEffects(phoneAppViewModel, onSettled = homeViewModel::onLocateRequested)

    TripEventEffect(
        viewModel = tripViewModel,
        snackbar = snackbar,
        scope = scope,
        onPlanReady = {
            closeSearch()
            scope.launch { sheetState.partialExpand() }
        },
        onOpenGarage = { navigator.openFromRoot(Garage) },
        onCommitted = { navigator.openFromRoot(ActiveRoute) },
    )

    PhoneAppDrawer(
        drawerState = drawerState,
        uiState = drawerUi,
        // The drawer stays open underneath the page.
        onOpen = { target -> navigator.openFromRoot(target.destination) },
        onFilters = drawerViewModel::onFiltersChanged,
    ) {
        TripSheetScaffold(
            trip = planned,
            layout = phoneAppUi.tripLayout,
            sheetState = sheetState,
            snackbar = snackbar,
            onLayoutChanged = phoneAppViewModel::onTripLayoutChanged,
            // Same destination, today's settings; the charge level is asked for unless the car reports it.
            onReplan = tripViewModel::onReplanRequested,
            onOpenStop = { stop -> homeViewModel.onSiteSelected(stop.site) },
            // Sending is committing: the plan becomes the active route, whose page is confirmation enough.
            onSendToMaps = { url ->
                if (context.openMapsLink(url)) tripViewModel.commit()
            },
            viewModel = tripViewModel,
        ) { peek ->
            // Just the map, built once and kept. Pages and sheets are a
            // separate layer above the drawer (below).
            HomeRoute(
                hasPermission = phoneAppUi.hasLocationPermission,
                planningInProgress = tripUi is TripUiState.Planning,
                mode = when {
                    phoneAppUi.searching -> HomeMode.SEARCHING
                    planned != null -> HomeMode.TRIP
                    else -> HomeMode.BROWSING
                },
                route = remember(planned?.plan) { planned?.plan?.toRouteOverlay() },
                // The fit runs the moment the trip appears; the sheet is still rising then.
                mapBottomInset = if (planned != null) tripPeekHeight() else 0.dp,
                onRequestPermission = phoneAppViewModel::onLocationPermissionRequested,
                onLocate = phoneAppViewModel::onLocateRequested,
                onSettings = { scope.launch { drawerState.open() } },
                onChargeNow = { navigator.open(ChargeNow) },
                activeRouteEnabled = committedUi.trip != null,
                onActiveRoute = { navigator.openFromRoot(ActiveRoute) },
                onDismissSearch = ::closeSearch,
                onStopTapped = { index ->
                    planned?.plan?.stops?.getOrNull(index - 1)?.let { homeViewModel.onSiteSelected(it.site) }
                },
                // A stop opened from the active route page belongs to the committed plan, not to one being looked at.
                tripLineFor = { selected ->
                    val plan = if (navigator.backStack.lastOrNull() == ActiveRoute) committedUi.trip?.plan else planned?.plan
                    plan?.stopLine(context, selected, clock())
                },
                topBar = { flyTo ->
                    val trip = planned
                    // Header and bar swap with Material's fade-through instead of a hard cut.
                    AnimatedContent(
                        targetState = trip != null && !phoneAppUi.searching,
                        transitionSpec = { fadeIn(ChargeAheadMotion.fadeThroughIn()) togetherWith fadeOut(ChargeAheadMotion.fadeThroughOut()) },
                        label = "top slot",
                    ) { showHeader ->
                    if (showHeader && trip != null) {
                        DestinationHeader(
                            // The short name (town, or street and number), not the full label.
                            title = trip.plan.destination.name,
                            subtitle = trip.plan.headerLine(),
                            onClear = tripViewModel::clear,
                            onTitleClick = { flyTo(trip.plan.destination.position) },
                            // Material's bar brings its own surface, the header has to bring one too.
                            standalone = true,
                        )
                    } else {
                        HomeDockedSearchBar(
                            query = searchUi.query,
                            searching = searchUi.searching,
                            expanded = phoneAppUi.searching,
                            onExpandedChange = { open -> if (open) phoneAppViewModel.onSearchOpened() else closeSearch() },
                            onQueryChange = searchViewModel::onQueryChanged,
                            onClear = { searchViewModel.onQueryChanged("") },
                        ) {
                            SearchResultsPanel(uiState = searchUi, onPick = ::pickRow, standalone = false)
                        }
                    }
                    }
                },
                // No scaffold padding: the map draws under the status bar.
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }

    PhoneNavDisplay(
        navigator = navigator,
        librariesRes = librariesRes,
        preferredNetworkCount = drawerUi.preferredNetworkCount,
        onCarAdded = { preset -> snackbar.show(scope, context.getString(R.string.garage_added, preset.name)) },
        onNavigateTo = { position -> sendToMaps(MapsHandoff.navigateUrl(position)) },
        onOpenStop = { stop -> homeViewModel.onSiteSelected(stop.site) },
        onSendToMaps = ::sendToMaps,
        onTripEnded = { snackbar.show(scope, context.getString(R.string.active_route_ended)) },
        activeRouteTitle = activeRouteTitle,
        modifier = Modifier.fillMaxSize(),
    )

    // What back takes away on the map screen, outermost first. No step for the
    // drawer: ModalNavigationDrawer closes itself on back, and a step here would
    // take that — and its predictive-back animation — away from it.
    navigator.ScreenStep(
        when {
            drawerState.isOpen -> null
            phoneAppUi.searching -> ::closeSearch
            planned != null && sheetState.currentValue == SheetValue.Expanded -> ::collapseTripSheet
            planned != null -> tripViewModel::clear
            else -> null
        },
    )
    BackHandler(enabled = navigator.ownsBack) { navigator.back() }
}

/** The settings drawer, from the right edge. */
@Composable
private fun PhoneAppDrawer(
    drawerState: DrawerState,
    uiState: DrawerUiState,
    onOpen: (DrawerTarget) -> Unit,
    onFilters: (ChargeFilters) -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the burger: the edge swipe fights the map's pan gesture.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            // The overload with the state is the one that handles back itself.
            ModalDrawerSheet(drawerState, drawerContainerColor = MaterialTheme.colorScheme.surface) {
                DrawerContent(uiState = uiState, onOpen = onOpen, onFilters = onFilters)
            }
        },
        content = content,
    )
}

/** Turns each outcome of planning and saving into a snackbar or a screen change, once. */
@Composable
private fun TripEventEffect(
    viewModel: TripViewModel,
    snackbar: SnackbarHostState,
    // Outlives the effect: consuming the event restarts it, which would cancel the message.
    scope: CoroutineScope,
    onPlanReady: () -> Unit,
    onOpenGarage: () -> Unit,
    onCommitted: () -> Unit,
) {
    val context = LocalContext.current
    val event by viewModel.event.collectAsStateWithLifecycle()

    LaunchedEffect(event) {
        when (val current = event) {
            null -> return@LaunchedEffect
            TripEvent.PlanReady -> onPlanReady()
            TripEvent.VehicleMissing -> scope.launch {
                val result = snackbar.showSnackbar(
                    message = context.getString(R.string.plan_vehicle_missing),
                    actionLabel = context.getString(R.string.plan_vehicle_missing_action),
                )
                if (result == SnackbarResult.ActionPerformed) onOpenGarage()
            }
            is TripEvent.NoChargerInReach -> snackbar.show(
                scope,
                context.getString(R.string.plan_failed_no_charger, current.afterKm.roundToInt()),
            )
            TripEvent.NoRoute -> snackbar.show(scope, context.getString(R.string.plan_failed_no_route))
            TripEvent.TripCommitted -> onCommitted()
        }
        viewModel.onEventHandled()
    }
}

private fun TripPlan.toRouteOverlay() = RouteOverlay(
    points = route.points,
    stops = stops.mapIndexed { index, stop -> RouteStop(index + 1, stop.site.position, operatorColor(stop.site)) },
    destination = destination.position,
)

/** Arrival and departure, when [selected] is one of this trip's stops. */
private fun TripPlan.stopLine(context: Context, selected: ChargeStop, now: LocalTime): String? =
    stops.firstOrNull { it.site.id == selected.site.id }?.let { stop ->
        context.getString(
            R.string.trip_stop_times,
            etaText(stop.arrivalMinutesFromStart, now),
            etaText(stop.arrivalMinutesFromStart + stop.chargeMinutes, now),
        )
    }

/** Shows [message] in a scope that outlives the effect that triggered it. */
private fun SnackbarHostState.show(scope: CoroutineScope, message: String) {
    scope.launch { showSnackbar(message) }
}
