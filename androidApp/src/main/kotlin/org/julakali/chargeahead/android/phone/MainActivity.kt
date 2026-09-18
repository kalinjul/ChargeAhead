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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import org.julakali.chargeahead.shared.ui.PlanSheetViewModel
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

private enum class Sheet { NONE, PLAN, CHARGE_NOW, ROUTES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneApp() {
    val context = LocalContext.current

    // State holders read by the chrome (app bar, drawer, sheets).
    val drawerViewModel: DrawerViewModel = koinViewModel()
    val homeViewModel: HomeViewModel = koinViewModel()
    val tripViewModel: TripViewModel = koinViewModel()
    val planSheetViewModel: PlanSheetViewModel = koinViewModel()
    val chargeNowViewModel: ChargeNowViewModel = koinViewModel()

    val drawerUi by drawerViewModel.uiState.collectAsStateWithLifecycle()
    val tripUi by tripViewModel.uiState.collectAsStateWithLifecycle()
    val tripEvent by tripViewModel.event.collectAsStateWithLifecycle()

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

    fun sendToMaps(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            scope.launch { snackbar.showSnackbar(context.getString(R.string.trip_maps_sent)) }
        } catch (notFound: ActivityNotFoundException) {
            Toast.makeText(context, R.string.phone_detail_no_navigation, Toast.LENGTH_LONG).show()
        }
    }

    // Outcomes of planning and saving, once each.
    LaunchedEffect(tripEvent) {
        when (val event = tripEvent) {
            null -> return@LaunchedEffect
            TripEvent.PlanReady -> openFromRoot(Trip)
            TripEvent.VehicleMissing -> snackbar.show(scope, context.getString(R.string.plan_vehicle_missing))
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the burger: the edge swipe fights the map's pan gesture.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                val applyingFilters by homeViewModel.applyingFilters.collectAsStateWithLifecycle()
                DrawerContent(
                    uiState = drawerUi,
                    applyingFilters = applyingFilters,
                    // The drawer stays open underneath the page.
                    onOpen = { target -> openFromRoot(target) },
                    onFilters = drawerViewModel::onFiltersChanged,
                )
            }
        },
    ) {
        Scaffold(
            // Every page brings its own top bar, so keep clear of the navigation bar here.
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            // Just the map, built once and kept. Full-screen pages are a
            // separate layer above the drawer (below).
            HomeRoute(
                hasPermission = hasPermission,
                planningInProgress = tripUi is TripUiState.Planning,
                onRequestPermission = ::requestLocationPermission,
                onLocate = ::onLocate,
                onMenu = { scope.launch { drawerState.open() } },
                onPlan = { sheet = Sheet.PLAN; planSheetViewModel.onSheetOpened() },
                onChargeNow = { sheet = Sheet.CHARGE_NOW; chargeNowViewModel.onSheetOpened() },
                onRoutes = { sheet = Sheet.ROUTES },
                // No scaffold padding: the map draws under the status bar.
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
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

                    entry<Trip> {
                        val trip = planned
                        if (trip == null) {
                            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        } else {
                            Page(
                                title = "→ ${ChargeStopFormatter.label(trip.plan.destination)}",
                                subtitle = pluralStringResource(
                                    R.plurals.trip_topbar_sub,
                                    trip.plan.stops.size,
                                    trip.plan.stops.size,
                                ),
                                onBack = ::pop,
                            ) { pagePadding ->
                                TripPlanScreen(
                                    plan = trip.plan,
                                    startPosition = trip.startPosition,
                                    startSocPercent = trip.startSocPercent,
                                    isSaved = trip.isSaved,
                                    hasLocationPermission = hasPermission,
                                    selection = trip.selection,
                                    socInput = trip.socInput,
                                    arrivalSocInput = trip.arrivalSocInput,
                                    onToggleSelecting = tripViewModel::onSectionSelectingToggled,
                                    onPickPoint = tripViewModel::onSectionPointPicked,
                                    onSectionSent = tripViewModel::onSectionSent,
                                    onOpenStop = { stop -> backStack.add(StopDetail(trip.plan.stops.indexOf(stop))) },
                                    onSendToMaps = ::sendToMaps,
                                    onToggleSave = { tripViewModel.toggleSaved(trip.plan.summaryLine(context)) },
                                    onReplan = {
                                        planSheetViewModel.onSheetOpened(trip.plan.destination)
                                        sheet = Sheet.PLAN
                                    },
                                    onEditStartSoc = tripViewModel::onStartSocEditRequested,
                                    onSocInputChange = tripViewModel::onStartSocInputChanged,
                                    onSocConfirm = tripViewModel::onStartSocConfirmed,
                                    onSocDismiss = tripViewModel::onStartSocEditDismissed,
                                    onEditArrivalSoc = tripViewModel::onArrivalSocEditRequested,
                                    onArrivalSocInputChange = tripViewModel::onArrivalSocInputChanged,
                                    onArrivalSocConfirm = tripViewModel::onArrivalSocConfirmed,
                                    onArrivalSocDismiss = tripViewModel::onArrivalSocEditDismissed,
                                    modifier = Modifier.fillMaxSize().padding(pagePadding),
                                )
                            }
                        }
                    }

                    entry<StopDetail> { key ->
                        val plan = planned?.plan
                        val stop = plan?.stops?.getOrNull(key.index)
                        if (plan == null || stop == null) {
                            pop()
                        } else {
                            Page(
                                title = stringResource(R.string.detail_title),
                                subtitle = stringResource(
                                    R.string.detail_stop_x_of_y,
                                    key.index + 1,
                                    plan.stops.size,
                                ),
                                onBack = ::pop,
                            ) { pagePadding ->
                                StopDetailScreen(
                                    stop = stop,
                                    onSendToMaps = ::sendToMaps,
                                    modifier = Modifier.fillMaxSize().padding(pagePadding),
                                )
                            }
                        }
                    }

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
        },
        modifier = Modifier.fillMaxSize(),
    )

    when (sheet) {
        Sheet.NONE -> Unit

        Sheet.PLAN -> AppSheet(onDismissRequest = { sheet = Sheet.NONE }) {
            PlanSheetRoute(
                onPlan = { destination, socPercent ->
                    sheet = Sheet.NONE
                    tripViewModel.plan(destination, socPercent)
                },
            )
        }

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

    // A sheet, or the drawer once no page is over it, closes on back.
    BackHandler(enabled = sheet != Sheet.NONE || (drawerState.isOpen && backStack.size == 1)) {
        when {
            sheet != Sheet.NONE -> sheet = Sheet.NONE
            else -> scope.launch { drawerState.close() }
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
