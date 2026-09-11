package de.autoapp.android.phone

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppSheet
import de.autoapp.android.phone.components.AppTopBar
import de.autoapp.android.phone.components.TopBarIcon
import de.autoapp.android.phone.theme.ChargeAheadTheme
import de.autoapp.shared.core.MapsHandoff
import de.autoapp.shared.core.TripPlan
import de.autoapp.shared.ui.ChargeNowViewModel
import de.autoapp.shared.ui.DrawerViewModel
import de.autoapp.shared.ui.HomeViewModel
import de.autoapp.shared.ui.PlanSheetViewModel
import de.autoapp.shared.ui.TripEvent
import de.autoapp.shared.ui.TripUiState
import de.autoapp.shared.ui.TripViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The phone app: map-first, the flows from the design mockup (docs/mockup) —
 * plan a route with charging stops, "charge now", garage, subscriptions,
 * filters, saved routes.
 *
 * State lives in the shared ViewModels (`de.autoapp.shared.ui`); what stays
 * here is what is genuinely the app shell's: the Navigation3 back stack,
 * which sheet is open, and the Android-only permission handshake.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is light-only, but enableEdgeToEdge() reads the SYSTEM theme:
        // in system dark mode the status-bar icons went white and vanished over
        // the light map. Pin the light style so they stay dark.
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

    // The chrome's own state holders. Every screen below fetches its own —
    // these three are here because the app bar, the drawer and the sheets
    // read them, not one screen.
    val drawerViewModel: DrawerViewModel = phoneViewModel()
    // Same activity-scoped instance the map uses; the drawer reads its
    // applyingFilters to show a spinner while a filter toggle refetches.
    val homeViewModel: HomeViewModel = phoneViewModel()
    val tripViewModel: TripViewModel = phoneViewModel()
    val planSheetViewModel: PlanSheetViewModel = phoneViewModel()
    val chargeNowViewModel: ChargeNowViewModel = phoneViewModel()

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

    // Drawer targets never stack on each other: back from any of them goes
    // home, exactly as the enum navigation behaved.
    fun openFromRoot(target: PhoneDestination) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (target != Home) backStack.add(target)
    }

    var hasPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasPermission = results.values.any { it }
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

    // Outcomes of planning and saving, once each. The snackbar runs in the
    // remembered scope rather than in this effect: consuming the event
    // changes the key, and that would cancel the effect mid-message.
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
        // Open only via the burger: the edge swipe fights the map's pan
        // gesture and wins far too often.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                val homeUi by homeViewModel.uiState.collectAsStateWithLifecycle()
                DrawerContent(
                    uiState = drawerUi,
                    applyingFilters = homeUi.applyingFilters,
                    // The drawer stays open: the page slides in over it from the
                    // right, and back slides it away to reveal the drawer again.
                    onOpen = { target -> openFromRoot(target) },
                    onFilters = drawerViewModel::onFiltersChanged,
                )
            }
        },
    ) {
        Scaffold(
            // The snackbar is the only chrome left out here. Every page brings
            // its own top bar from inside NavDisplay, so the insets are zero
            // and the host has to keep clear of the navigation bar itself.
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            // Just the map. Full-screen pages are a separate layer above the
            // drawer (below), so they slide in over the still-open drawer. The
            // map is built once here and kept — rebuilding the GoogleMap on
            // every back press is what stalled the UI thread (fix/menu-back-lag).
            HomeRoute(
                hasPermission = hasPermission,
                planningInProgress = tripUi is TripUiState.Planning,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                onMenu = { scope.launch { drawerState.open() } },
                onPlan = { sheet = Sheet.PLAN; planSheetViewModel.onSheetOpened() },
                onChargeNow = { sheet = Sheet.CHARGE_NOW; chargeNowViewModel.onSheetOpened() },
                onRoutes = { sheet = Sheet.ROUTES },
                // No scaffold padding: the map draws under the (dark-iconed)
                // status bar, like every maps app.
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }

    // Full-screen pages: a layer ABOVE the drawer that slides in from the right
    // and slides back out to reveal the still-open drawer. Predictive back drags
    // it rightward. The empty Home slot lets the map + drawer show through.
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        transitionSpec = {
            slideInHorizontally(tween(300)) { it } togetherWith fadeOut(tween(300))
        },
        popTransitionSpec = {
            fadeIn(tween(300)) togetherWith slideOutHorizontally(tween(300)) { it }
        },
        entryProvider = entryProvider {
            // Home is the map + drawer below; its slot is empty.
            entry<Home> { }

                    entry<Trip> {
                        val trip = planned
                        if (trip == null) {
                            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        } else {
                            Page(
                                title = "→ ${trip.plan.destination.name}",
                                subtitle = pluralStringResource(
                                    R.plurals.trip_topbar_sub,
                                    trip.plan.stops.size,
                                    trip.plan.stops.size,
                                ),
                                onBack = ::pop,
                                actions = {
                                    TopBarIcon(
                                        painterResource(R.drawable.ic_filter),
                                        stringResource(R.string.trip_filters),
                                        onClick = { scope.launch { drawerState.open() } },
                                        badge = drawerUi.filtersCustomized,
                                    )
                                },
                            ) { pagePadding ->
                                TripPlanScreen(
                                    plan = trip.plan,
                                    startPosition = trip.startPosition,
                                    startSocPercent = trip.startSocPercent,
                                    isSaved = trip.isSaved,
                                    isEstimate = trip.isEstimate,
                                    hasLocationPermission = hasPermission,
                                    selection = trip.selection,
                                    socInput = trip.socInput,
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

                    entry<Subscriptions> {
                        Page(title = stringResource(R.string.subs_title), onBack = ::pop) { pagePadding ->
                            SubscriptionsRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
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
                // Reopening a route from the list keeps the stored charge
                // level; adjusting it is what the plan sheet is for.
                onOpen = { destination ->
                    sheet = Sheet.NONE
                    tripViewModel.plan(destination)
                },
            )
        }
    }

    // A sheet, or the drawer once no page is over it, closes on back. While a
    // page IS open the drawer stays open underneath, so back must pop the page
    // (NavDisplay's job) — hence the size check keeps this handler out of the way.
    BackHandler(enabled = sheet != Sheet.NONE || (drawerState.isOpen && backStack.size == 1)) {
        when {
            sheet != Sheet.NONE -> sheet = Sheet.NONE
            else -> scope.launch { drawerState.close() }
        }
    }
}

/**
 * One page of the back stack: a full-screen, opaque Scaffold with its own top
 * bar.
 *
 * The bar belongs in here rather than in an outer Scaffold. Predictive back
 * scales down the whole NavDisplay entry, so anything hoisted above it stays
 * behind, hanging over a page that shrinks away — and an entry that paints no
 * background of its own is see-through while it does, showing the page
 * underneath straight through the shrinking one.
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

/** Shown under a saved route's name — built from the same resources the trip header uses. */
private fun TripPlan.summaryLine(context: Context): String =
    context.getString(R.string.trip_summary_distance, route.distanceKm.roundToInt()) + " · " +
        context.resources.getQuantityString(R.plurals.trip_summary_stops, stops.size, stops.size)

/**
 * Shows [message] in a scope that outlives the effect that triggered it —
 * a snackbar must not die because the state that caused it was consumed.
 */
private fun SnackbarHostState.show(scope: CoroutineScope, message: String) {
    scope.launch { showSnackbar(message) }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
