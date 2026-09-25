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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import org.julakali.chargeahead.android.phone.components.AppSheet
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.core.MapsHandoff
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.ui.DrawerUiState
import org.julakali.chargeahead.shared.ui.DrawerViewModel
import org.julakali.chargeahead.shared.ui.HomeViewModel
import org.julakali.chargeahead.shared.ui.SearchViewModel
import org.julakali.chargeahead.shared.ui.ShellSheet
import org.julakali.chargeahead.shared.ui.ShellViewModel
import org.julakali.chargeahead.shared.ui.TripEvent
import org.julakali.chargeahead.shared.ui.TripUiState
import org.julakali.chargeahead.shared.ui.TripViewModel
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * The phone app shell: wires the map chrome, the page back stack and the
 * sheets together. [librariesRes] is the AboutLibraries JSON, generated in the
 * app module that owns all dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneApp(librariesRes: Int) {
    val shellViewModel: ShellViewModel = koinViewModel()
    val drawerViewModel: DrawerViewModel = koinViewModel()
    val homeViewModel: HomeViewModel = koinViewModel()
    val tripViewModel: TripViewModel = koinViewModel()
    val searchViewModel: SearchViewModel = koinViewModel()

    val shellUi by shellViewModel.uiState.collectAsStateWithLifecycle()
    val drawerUi by drawerViewModel.uiState.collectAsStateWithLifecycle()
    val tripUi by tripViewModel.uiState.collectAsStateWithLifecycle()
    val searchUi by searchViewModel.uiState.collectAsStateWithLifecycle()
    val planned = tripUi as? TripUiState.Planned

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val clock = LocalNow.current
    val backStack = rememberNavBackStack(Home)

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    // Drawer targets never stack on each other.
    fun openFromRoot(target: PhoneDestination) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (target != Home) backStack.add(target)
    }

    fun closeSearch() {
        shellViewModel.onSearchClosed()
        focusManager.clearFocus()
        searchViewModel.onClosed()
    }

    fun sendToMaps(link: String) {
        if (context.openMapsLink(link)) snackbar.show(scope, context.getString(R.string.trip_maps_sent))
    }

    LocationHandshakeEffects(shellViewModel, onSettled = homeViewModel::onLocateRequested)

    TripEventEffect(
        viewModel = tripViewModel,
        snackbar = snackbar,
        scope = scope,
        onPlanReady = {
            closeSearch()
            scope.launch { sheetState.partialExpand() }
        },
        onOpenGarage = { openFromRoot(Garage) },
    )

    ShellDrawer(
        drawerState = drawerState,
        uiState = drawerUi,
        // The drawer stays open underneath the page.
        onOpen = ::openFromRoot,
        onFilters = drawerViewModel::onFiltersChanged,
    ) {
        TripSheetScaffold(
            trip = planned,
            layout = shellUi.tripLayout,
            sheetState = sheetState,
            snackbar = snackbar,
            onLayoutChanged = shellViewModel::onTripLayoutChanged,
            onReplan = {
                planned?.let { searchViewModel.onOpened(it.plan.destination) }
                shellViewModel.onSearchOpened()
            },
            onOpenStop = { stop -> homeViewModel.onSiteSelected(stop.site) },
            onSendToMaps = ::sendToMaps,
            viewModel = tripViewModel,
        ) { peek ->
            // Just the map, built once and kept. Full-screen pages are a
            // separate layer above the drawer (below).
            HomeRoute(
                hasPermission = shellUi.hasLocationPermission,
                planningInProgress = tripUi is TripUiState.Planning,
                mode = when {
                    shellUi.searching -> HomeMode.SEARCHING
                    planned != null -> HomeMode.TRIP
                    else -> HomeMode.BROWSING
                },
                route = remember(planned?.plan) { planned?.plan?.toRouteOverlay() },
                mapBottomInset = peek,
                onRequestPermission = shellViewModel::onLocationPermissionRequested,
                onLocate = shellViewModel::onLocateRequested,
                onSettings = { scope.launch { drawerState.open() } },
                onChargeNow = { shellViewModel.onSheetOpened(ShellSheet.CHARGE_NOW) },
                onRoutes = { shellViewModel.onSheetOpened(ShellSheet.ROUTES) },
                onDismissSearch = ::closeSearch,
                onStopTapped = { index ->
                    planned?.plan?.stops?.getOrNull(index - 1)?.let { homeViewModel.onSiteSelected(it.site) }
                },
                tripLineFor = { selected -> planned?.plan?.stopLine(context, selected, clock()) },
                topBar = {
                    val trip = planned
                    if (trip != null && !shellUi.searching) {
                        DestinationHeader(
                            title = ChargeStopFormatter.label(trip.plan.destination),
                            subtitle = trip.plan.headerLine(),
                            onClear = tripViewModel::clear,
                        )
                    } else {
                        HomeSearchBar(
                            query = searchUi.query,
                            searching = searchUi.searching,
                            onFocused = shellViewModel::onSearchOpened,
                            onQueryChange = searchViewModel::onQueryChanged,
                            // One tap back to the plain map, whatever was typed or planned.
                            onClear = {
                                closeSearch()
                                tripViewModel.clear()
                            },
                            focusRequester = focusRequester,
                            clearable = shellUi.searching,
                            takeFocus = shellUi.searching,
                        )
                    }
                },
                topPanel = {
                    SearchResultsPanel(
                        uiState = searchUi,
                        // No vehicle? Planning reports it as an event.
                        onPick = { row ->
                            closeSearch()
                            tripViewModel.plan(row.destination)
                        },
                    )
                },
                // No scaffold padding: the map draws under the status bar.
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }

    PhonePages(
        backStack = backStack,
        librariesRes = librariesRes,
        preferredNetworkCount = drawerUi.preferredNetworkCount,
        onBack = ::pop,
        onCarAdded = { preset -> snackbar.show(scope, context.getString(R.string.garage_added, preset.name)) },
        modifier = Modifier.fillMaxSize(),
    )

    ShellSheets(
        sheet = shellUi.sheet,
        onDismiss = shellViewModel::onSheetDismissed,
        onNavigate = { position -> sendToMaps(MapsHandoff.navigateUrl(position)) },
        onOpenRoute = { destination ->
            shellViewModel.onSheetDismissed()
            // Reopening a route keeps the stored charge level.
            tripViewModel.plan(destination)
        },
    )

    // With no page on top, back peels the chrome layer by layer: sheet,
    // drawer, search, expanded trip sheet, the trip itself.
    val atRoot = backStack.size == 1
    val sheetExpanded = planned != null && sheetState.currentValue == SheetValue.Expanded
    val sheetOpen = shellUi.sheet != ShellSheet.NONE
    BackHandler(
        enabled = sheetOpen || (atRoot && (drawerState.isOpen || shellUi.searching || sheetExpanded || planned != null)),
    ) {
        when {
            sheetOpen -> shellViewModel.onSheetDismissed()
            drawerState.isOpen -> scope.launch { drawerState.close() }
            shellUi.searching -> closeSearch()
            sheetExpanded -> scope.launch { sheetState.partialExpand() }
            planned != null -> tripViewModel.clear()
        }
    }
}

/** The settings drawer, from the right edge. */
@Composable
private fun ShellDrawer(
    drawerState: DrawerState,
    uiState: DrawerUiState,
    onOpen: (PhoneDestination) -> Unit,
    onFilters: (ChargeFilters) -> Unit,
    content: @Composable () -> Unit,
) {
    // Material's drawer only knows the start edge; in RTL that edge is the right.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // Open only via the settings icon: the edge swipe fights the map's pan gesture.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                        DrawerContent(uiState = uiState, onOpen = onOpen, onFilters = onFilters)
                    }
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                content()
            }
        }
    }
}

@Composable
private fun ShellSheets(
    sheet: ShellSheet,
    onDismiss: () -> Unit,
    onNavigate: (LatLon) -> Unit,
    onOpenRoute: (Destination) -> Unit,
) {
    when (sheet) {
        ShellSheet.NONE -> Unit

        ShellSheet.CHARGE_NOW -> AppSheet(onDismissRequest = onDismiss) {
            ChargeNowRoute(onNavigate = { candidate -> onNavigate(candidate.site.position) })
        }

        ShellSheet.ROUTES -> AppSheet(onDismissRequest = onDismiss) {
            RoutesRoute(onOpen = onOpenRoute)
        }
    }
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
            TripEvent.RouteSaved -> snackbar.show(scope, context.getString(R.string.trip_saved))
            TripEvent.RouteRemoved -> snackbar.show(scope, context.getString(R.string.trip_unsaved))
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
