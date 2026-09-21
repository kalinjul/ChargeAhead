package org.julakali.chargeahead.android.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.android.phone.components.AppSheet
import org.julakali.chargeahead.android.phone.components.AppTopBar
import org.julakali.chargeahead.android.phone.theme.ChargeAheadTheme
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.core.MapsHandoff
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.ui.ChargeNowViewModel
import org.julakali.chargeahead.shared.ui.DrawerViewModel
import org.julakali.chargeahead.shared.ui.HomeViewModel
import org.julakali.chargeahead.shared.ui.SearchViewModel
import org.julakali.chargeahead.shared.ui.TripEvent
import org.julakali.chargeahead.shared.ui.TripUiState
import org.julakali.chargeahead.shared.ui.TripViewModel
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The phone app shell: the Navigation3 back stack, which sheet is open, and
 * the Android-only permission handshake.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is light-only; pin the light system bar style.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ChargeAheadTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhoneApp()
                }
            }
        }
    }
}

private enum class Sheet { NONE, CHARGE_NOW, ROUTES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneApp() {
    val context = LocalContext.current

    // State holders read by the chrome (drawer, bar, sheets).
    val drawerViewModel: DrawerViewModel = koinViewModel()
    val homeViewModel: HomeViewModel = koinViewModel()
    val tripViewModel: TripViewModel = koinViewModel()
    val searchViewModel: SearchViewModel = koinViewModel()
    val chargeNowViewModel: ChargeNowViewModel = koinViewModel()

    val drawerUi by drawerViewModel.uiState.collectAsStateWithLifecycle()
    val tripUi by tripViewModel.uiState.collectAsStateWithLifecycle()
    val tripEvent by tripViewModel.event.collectAsStateWithLifecycle()
    val searchUi by searchViewModel.uiState.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val backStack = rememberNavBackStack(Home)
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    // Drawer targets never stack on each other.
    fun openFromRoot(target: PhoneDestination) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (target != Home) backStack.add(target)
    }

    // The device-settings half of the location handshake.
    val checkLocationSettings = rememberLocationSettingsCheck {
        homeViewModel.onLocateRequested()
    }

    // Set when the location button had to ask for the permission first.
    var locateAfterPermission by remember { mutableStateOf(false) }

    var hasPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasPermission = results.values.any { it }
        if (hasPermission && locateAfterPermission) checkLocationSettings()
        locateAfterPermission = false
    }

    // Re-read on every resume: the grant can happen outside this launcher.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = context.hasLocationPermission()
    }

    fun requestLocationPermission() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    /** Everything the location button needs from the platform, in order. */
    fun onLocate() {
        if (hasPermission) {
            checkLocationSettings()
        } else {
            locateAfterPermission = true
            requestLocationPermission()
        }
    }

    val planned = tripUi as? TripUiState.Planned

    // Searching is a UI mode: the bar has focus and the panel is open.
    var searching by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun closeSearch() {
        searching = false
        focusManager.clearFocus()
        searchViewModel.onClosed()
    }

    val mode = when {
        searching -> HomeMode.SEARCHING
        planned != null -> HomeMode.TRIP
        else -> HomeMode.BROWSING
    }

    val routeOverlay = remember(planned?.plan) {
        planned?.plan?.let { plan ->
            RouteOverlay(
                points = plan.route.points,
                stops = plan.stops.mapIndexed { index, stop -> (index + 1) to stop.site.position },
                destination = plan.destination.position,
            )
        }
    }

    // Tiles fit the peek and never expand; the missing drag handle says so.
    var tripLayout by rememberSaveable { mutableStateOf(TripListLayout.LIST) }
    val expandable = tripLayout == TripListLayout.LIST

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    LaunchedEffect(expandable) {
        if (!expandable) sheetState.partialExpand()
    }

    // The scaffold snaps to a new peek; animating the value makes it glide.
    val peek by animateDpAsState(
        targetValue = if (planned != null) tripPeekHeight(tripLayout) else 0.dp,
        animationSpec = tween(260),
        label = "sheet peek",
    )

    fun sendToMaps(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            scope.launch { snackbar.showSnackbar(context.getString(R.string.trip_maps_sent)) }
        } catch (notFound: ActivityNotFoundException) {
            Toast.makeText(context, R.string.phone_detail_no_navigation, Toast.LENGTH_LONG).show()
        }
    }

    fun vehicleMissing() {
        scope.launch {
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.plan_vehicle_missing),
                actionLabel = context.getString(R.string.plan_vehicle_missing_action),
            )
            if (result == SnackbarResult.ActionPerformed) openFromRoot(Garage)
        }
    }

    // Outcomes of planning and saving, once each.
    LaunchedEffect(tripEvent) {
        when (val event = tripEvent) {
            null -> return@LaunchedEffect
            TripEvent.PlanReady -> {
                closeSearch()
                scope.launch { sheetState.partialExpand() }
            }
            TripEvent.VehicleMissing -> vehicleMissing()
            is TripEvent.NoChargerInReach -> snackbar.show(
                scope,
                context.getString(R.string.plan_failed_no_charger, event.afterKm.roundToInt()),
            )

            TripEvent.NoRoute -> snackbar.show(scope, context.getString(R.string.plan_failed_no_route))
            TripEvent.RouteSaved -> snackbar.show(scope, context.getString(R.string.trip_saved))
            TripEvent.RouteRemoved -> snackbar.show(scope, context.getString(R.string.trip_unsaved))
        }
        tripViewModel.onEventHandled()
    }

    // Material's drawer only knows the start edge; in RTL that edge is the right.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // Open only via the settings icon: the edge swipe fights the map's pan gesture.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                        DrawerContent(
                            uiState = drawerUi,
                            // The drawer stays open underneath the page.
                            onOpen = { target -> openFromRoot(target) },
                            onFilters = drawerViewModel::onFiltersChanged,
                        )
                    }
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = peek,
                    sheetSwipeEnabled = planned != null && expandable,
                    sheetDragHandle = if (expandable) ({ BottomSheetDefaults.DragHandle() }) else null,
                    sheetContainerColor = MaterialTheme.colorScheme.surface,
                    snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
                    sheetContent = {
                        val trip = planned
                        if (trip != null) {
                            // Sheet content is measured against the whole screen, so tiles
                            // must be told the peek is all they get.
                            val sheetHeight = if (expandable) Modifier.fillMaxHeight(0.85f) else Modifier.height(peek)
                            Column(Modifier.fillMaxWidth().then(sheetHeight)) {
                                TripSummary(
                                    trip.plan,
                                    layout = tripLayout,
                                    onToggleLayout = {
                                        tripLayout = if (expandable) TripListLayout.TILES else TripListLayout.LIST
                                    },
                                    onReplan = {
                                        searchViewModel.onOpened(trip.plan.destination)
                                        searching = true
                                    },
                                )
                                TripSheetContent(
                                    plan = trip.plan,
                                    startPosition = trip.startPosition,
                                    startSocPercent = trip.startSocPercent,
                                    isSaved = trip.isSaved,
                                    layout = tripLayout,
                                    selection = trip.selection,
                                    socInput = trip.socInput,
                                    arrivalSocInput = trip.arrivalSocInput,
                                    onToggleSelecting = tripViewModel::onSectionSelectingToggled,
                                    onPickPoint = tripViewModel::onSectionPointPicked,
                                    onSectionSent = tripViewModel::onSectionSent,
                                    onOpenStop = { stop -> homeViewModel.onSiteSelected(stop.site) },
                                    onSendToMaps = ::sendToMaps,
                                    onToggleSave = { tripViewModel.toggleSaved(trip.plan.summaryLine(context)) },
                                    onEditStartSoc = tripViewModel::onStartSocEditRequested,
                                    onSocInputChange = tripViewModel::onStartSocInputChanged,
                                    onSocConfirm = tripViewModel::onStartSocConfirmed,
                                    onSocDismiss = tripViewModel::onStartSocEditDismissed,
                                    onEditArrivalSoc = tripViewModel::onArrivalSocEditRequested,
                                    onArrivalSocInputChange = tripViewModel::onArrivalSocInputChanged,
                                    onArrivalSocConfirm = tripViewModel::onArrivalSocConfirmed,
                                    onArrivalSocDismiss = tripViewModel::onArrivalSocEditDismissed,
                                    modifier = Modifier.weight(1f).navigationBarsPadding(),
                                )
                            }
                        }
                    },
                ) { _ ->
                    // Just the map, built once and kept. Full-screen pages are a
                    // separate layer above the drawer (below).
                    HomeRoute(
                        hasPermission = hasPermission,
                        planningInProgress = tripUi is TripUiState.Planning,
                        mode = mode,
                        route = routeOverlay,
                        mapBottomInset = peek,
                        onRequestPermission = ::requestLocationPermission,
                        onLocate = ::onLocate,
                        onSettings = { scope.launch { drawerState.open() } },
                        onChargeNow = { sheet = Sheet.CHARGE_NOW; chargeNowViewModel.onSheetOpened() },
                        onRoutes = { sheet = Sheet.ROUTES },
                        onDismissSearch = ::closeSearch,
                        onStopTapped = { index ->
                            planned?.plan?.stops?.getOrNull(index - 1)?.let { homeViewModel.onSiteSelected(it.site) }
                        },
                        tripLineFor = { selected ->
                            planned?.plan?.stops?.firstOrNull { it.site.id == selected.site.id }?.let { stop ->
                                context.getString(
                                    R.string.trip_stop_times,
                                    etaText(stop.arrivalMinutesFromStart),
                                    etaText(stop.arrivalMinutesFromStart + stop.chargeMinutes),
                                )
                            }
                        },
                        topBar = {
                            val trip = planned
                            if (trip != null && !searching) {
                                DestinationHeader(
                                    title = ChargeStopFormatter.label(trip.plan.destination),
                                    subtitle = trip.plan.headerLine(),
                                    onClear = tripViewModel::clear,
                                )
                            } else {
                                HomeSearchBar(
                                    query = searchUi.query,
                                    searching = searchUi.searching,
                                    onFocused = { searching = true },
                                    onQueryChange = searchViewModel::onQueryChanged,
                                    // One tap back to the plain map, whatever was typed or planned.
                                    onClear = {
                                        closeSearch()
                                        tripViewModel.clear()
                                    },
                                    focusRequester = focusRequester,
                                    clearable = searching,
                                    takeFocus = searching,
                                )
                            }
                        },
                        topPanel = {
                            SearchResultsPanel(
                                uiState = searchUi,
                                onPick = { row ->
                                    if (!searchUi.hasVehicle) {
                                        vehicleMissing()
                                    } else {
                                        closeSearch()
                                        tripViewModel.plan(row.destination)
                                    }
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
        }
    }

    // Full-screen pages: a layer ABOVE the drawer. The empty Home slot lets
    // the map + drawer show through.
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        // Each page gets its own ViewModelStore, cleared when it leaves the
        // back stack. The chrome above (drawer, map, sheets) is outside, so
        // its ViewModels stay activity-scoped.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            slideInHorizontally(tween(300)) { it } togetherWith fadeOut(tween(300))
        },
        popTransitionSpec = {
            fadeIn(tween(300)) togetherWith slideOutHorizontally(tween(300)) { it }
        },
        entryProvider = entryProvider {
            entry<Home> { }

            entry<Garage> {
                Page(title = stringResource(R.string.garage_title), onBack = ::pop) { pagePadding ->
                    GarageRoute(
                        onOpenAdvanced = { backStack.add(VehicleEdit) },
                        onOpenAdd = { backStack.add(AddCar) },
                        modifier = Modifier.fillMaxSize().padding(pagePadding),
                    )
                }
            }

            entry<AddCar> {
                Page(title = stringResource(R.string.garage_add_title), onBack = ::pop) { pagePadding ->
                    AddCarRoute(
                        onAdded = { preset ->
                            pop()
                            snackbar.show(scope, context.getString(R.string.garage_added, preset.name))
                        },
                        modifier = Modifier.fillMaxSize().padding(pagePadding),
                    )
                }
            }

            entry<VehicleEdit> {
                Page(title = stringResource(R.string.phone_settings_title), onBack = ::pop) { pagePadding ->
                    VehicleSettingsRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Networks> {
                Page(
                    title = stringResource(R.string.phone_networks_title),
                    subtitle = networksSummary(drawerUi.preferredNetworkCount),
                    onBack = ::pop,
                ) { pagePadding ->
                    NetworksRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<CarData> {
                Page(title = stringResource(R.string.cardata_title), onBack = ::pop) { pagePadding ->
                    CarDataDebugRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Legal> {
                Page(title = stringResource(R.string.drawer_legal), onBack = ::pop) { pagePadding ->
                    LegalScreen(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Licenses> {
                Page(title = stringResource(R.string.drawer_licenses), onBack = ::pop) { pagePadding ->
                    LicensesRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    when (sheet) {
        Sheet.NONE -> Unit

        Sheet.CHARGE_NOW -> AppSheet(onDismissRequest = { sheet = Sheet.NONE }) {
            ChargeNowRoute(
                onNavigate = { candidate -> sendToMaps(MapsHandoff.navigateUrl(candidate.site.position)) },
            )
        }

        Sheet.ROUTES -> AppSheet(onDismissRequest = { sheet = Sheet.NONE }) {
            RoutesRoute(
                // Reopening a route keeps the stored charge level.
                onOpen = { destination ->
                    sheet = Sheet.NONE
                    tripViewModel.plan(destination)
                },
            )
        }
    }

    // With no page on top, back peels the chrome layer by layer: sheet,
    // drawer, search, expanded trip sheet, the trip itself.
    val atRoot = backStack.size == 1
    val sheetExpanded = planned != null && sheetState.currentValue == SheetValue.Expanded
    BackHandler(
        enabled = sheet != Sheet.NONE ||
            (atRoot && (drawerState.isOpen || searching || sheetExpanded || planned != null)),
    ) {
        when {
            sheet != Sheet.NONE -> sheet = Sheet.NONE
            drawerState.isOpen -> scope.launch { drawerState.close() }
            searching -> closeSearch()
            sheetExpanded -> scope.launch { sheetState.partialExpand() }
            planned != null -> tripViewModel.clear()
        }
    }
}

/**
 * One page of the back stack: a full-screen, opaque Scaffold with its own top
 * bar, so predictive back scales the whole page.
 */
@Composable
private fun Page(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { AppTopBar(title = title, onBack = onBack, subtitle = subtitle, actions = actions) },
        content = content,
    )
}

/** Shown under a saved route's name. */
private fun TripPlan.summaryLine(context: Context): String =
    context.getString(R.string.trip_summary_distance, route.distanceKm.roundToInt()) + " · " +
        context.resources.getQuantityString(R.plurals.trip_summary_stops, stops.size, stops.size)

/** Shows [message] in a scope that outlives the effect that triggered it. */
private fun SnackbarHostState.show(scope: CoroutineScope, message: String) {
    scope.launch { showSnackbar(message) }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
