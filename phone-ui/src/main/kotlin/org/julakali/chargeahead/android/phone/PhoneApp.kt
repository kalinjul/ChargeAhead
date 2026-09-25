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
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.core.MapsHandoff
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.ui.DrawerUiState
import org.julakali.chargeahead.shared.ui.DrawerViewModel
import org.julakali.chargeahead.shared.ui.HomeViewModel
import org.julakali.chargeahead.shared.ui.SearchViewModel
import org.julakali.chargeahead.shared.ui.PhoneAppViewModel
import org.julakali.chargeahead.shared.ui.TripEvent
import org.julakali.chargeahead.shared.ui.TripUiState
import org.julakali.chargeahead.shared.ui.TripViewModel
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * The phone app: wires the map chrome and the navigator's destinations
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

    val phoneAppUi by phoneAppViewModel.uiState.collectAsStateWithLifecycle()
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
    val navigator = rememberPhoneNavigator()

    fun closeSearch() {
        phoneAppViewModel.onSearchClosed()
        focusManager.clearFocus()
        searchViewModel.onClosed()
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
            onReplan = {
                planned?.let { searchViewModel.onOpened(it.plan.destination) }
                phoneAppViewModel.onSearchOpened()
            },
            onOpenStop = { stop -> homeViewModel.onSiteSelected(stop.site) },
            onSendToMaps = ::sendToMaps,
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
                mapBottomInset = peek,
                onRequestPermission = phoneAppViewModel::onLocationPermissionRequested,
                onLocate = phoneAppViewModel::onLocateRequested,
                onSettings = { scope.launch { drawerState.open() } },
                onChargeNow = { navigator.open(ChargeNow) },
                onRoutes = { navigator.open(Routes) },
                onDismissSearch = ::closeSearch,
                onStopTapped = { index ->
                    planned?.plan?.stops?.getOrNull(index - 1)?.let { homeViewModel.onSiteSelected(it.site) }
                },
                tripLineFor = { selected -> planned?.plan?.stopLine(context, selected, clock()) },
                topBar = {
                    val trip = planned
                    if (trip != null && !phoneAppUi.searching) {
                        DestinationHeader(
                            title = ChargeStopFormatter.label(trip.plan.destination),
                            subtitle = trip.plan.headerLine(),
                            onClear = tripViewModel::clear,
                        )
                    } else {
                        HomeSearchBar(
                            query = searchUi.query,
                            searching = searchUi.searching,
                            onFocused = phoneAppViewModel::onSearchOpened,
                            onQueryChange = searchViewModel::onQueryChanged,
                            // One tap back to the plain map, whatever was typed or planned.
                            onClear = {
                                closeSearch()
                                tripViewModel.clear()
                            },
                            focusRequester = focusRequester,
                            clearable = phoneAppUi.searching,
                            takeFocus = phoneAppUi.searching,
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

    PhoneNavDisplay(
        navigator = navigator,
        librariesRes = librariesRes,
        preferredNetworkCount = drawerUi.preferredNetworkCount,
        onCarAdded = { preset -> snackbar.show(scope, context.getString(R.string.garage_added, preset.name)) },
        onNavigateTo = { position -> sendToMaps(MapsHandoff.navigateUrl(position)) },
        onOpenRoute = { destination ->
            navigator.back()
            // Reopening a route keeps the stored charge level.
            tripViewModel.plan(destination)
        },
        modifier = Modifier.fillMaxSize(),
    )

    // Only while the back stack is at the map: back then peels the chrome
    // layer by layer — drawer, search, expanded trip sheet, the trip itself.
    // A page or sheet on top takes back first and pops itself.
    val sheetExpanded = planned != null && sheetState.currentValue == SheetValue.Expanded
    BackHandler(
        enabled = navigator.atRoot && (drawerState.isOpen || phoneAppUi.searching || sheetExpanded || planned != null),
    ) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            phoneAppUi.searching -> closeSearch()
            sheetExpanded -> scope.launch { sheetState.partialExpand() }
            planned != null -> tripViewModel.clear()
        }
    }
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
