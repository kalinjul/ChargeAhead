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
import androidx.compose.foundation.layout.fillMaxSize
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
    val current = backStack.lastOrNull() as? PhoneDestination ?: Home
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
                DrawerContent(
                    uiState = drawerUi,
                    // Close first, then navigate: the page swap disposes the
                    // map and its jank freezes a concurrently running drawer
                    // animation — the drawer then just hangs there, open.
                    onOpen = { target -> scope.launch { drawerState.close(); openFromRoot(target) } },
                    onFilters = drawerViewModel::onFiltersChanged,
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (current != Home) {
                    AppTopBar(
                        title = when (current) {
                            Trip -> "→ ${planned?.plan?.destination?.name.orEmpty()}"
                            is StopDetail -> stringResource(R.string.detail_title)
                            Garage -> stringResource(R.string.garage_title)
                            AddCar -> stringResource(R.string.garage_add_title)
                            VehicleEdit -> stringResource(R.string.phone_settings_title)
                            Subscriptions -> stringResource(R.string.subs_title)
                            Networks -> stringResource(R.string.phone_networks_title)
                            CarData -> stringResource(R.string.cardata_title)
                            Home -> stringResource(R.string.app_name)
                        },
                        subtitle = when (val destination = current) {
                            Trip -> planned?.plan?.let {
                                pluralStringResource(R.plurals.trip_topbar_sub, it.stops.size, it.stops.size)
                            }

                            is StopDetail -> planned?.plan?.let { plan ->
                                plan.stops.getOrNull(destination.index)?.let {
                                    stringResource(R.string.detail_stop_x_of_y, destination.index + 1, plan.stops.size)
                                }
                            }

                            Networks -> networksSummary(drawerUi.preferredNetworkCount)
                            else -> null
                        },
                        onBack = ::pop,
                        actions = {
                            if (current == Trip) {
                                TopBarIcon(
                                    painterResource(R.drawable.ic_filter),
                                    stringResource(R.string.trip_filters),
                                    onClick = { scope.launch { drawerState.open() } },
                                    badge = drawerUi.filtersCustomized,
                                )
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavDisplay(
                backStack = backStack,
                onBack = { pop() },
                entryProvider = entryProvider {
                    entry<Home> {
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
                            // No scaffold padding: the map draws under the (now
                            // dark-iconed) status bar, like every maps app.
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    entry<Trip> {
                        planned?.let { trip ->
                            TripPlanScreen(
                                plan = trip.plan,
                                startPosition = trip.startPosition,
                                startSocPercent = trip.startSocPercent,
                                isSaved = trip.isSaved,
                                isEstimate = trip.isEstimate,
                                hasLocationPermission = hasPermission,
                                selection = trip.selection,
                                onToggleSelecting = tripViewModel::onSectionSelectingToggled,
                                onPickPoint = tripViewModel::onSectionPointPicked,
                                onSectionSent = tripViewModel::onSectionSent,
                                onOpenStop = { stop -> backStack.add(StopDetail(trip.plan.stops.indexOf(stop))) },
                                onSendToMaps = ::sendToMaps,
                                onToggleSave = { tripViewModel.toggleSaved(trip.plan.summaryLine(context)) },
                                modifier = Modifier.fillMaxSize().padding(padding),
                            )
                        } ?: run { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
                    }

                    entry<StopDetail> { key ->
                        planned?.plan?.stops?.getOrNull(key.index)?.let { stop ->
                            StopDetailScreen(
                                stop = stop,
                                onSendToMaps = ::sendToMaps,
                                modifier = Modifier.fillMaxSize().padding(padding),
                            )
                        } ?: run { pop() }
                    }

                    entry<Garage> {
                        GarageRoute(
                            onOpenAdvanced = { backStack.add(VehicleEdit) },
                            onOpenAdd = { backStack.add(AddCar) },
                            modifier = Modifier.fillMaxSize().padding(padding),
                        )
                    }

                    entry<AddCar> {
                        AddCarRoute(
                            onAdded = { preset ->
                                pop()
                                snackbar.show(scope, context.getString(R.string.garage_added, preset.name))
                            },
                            modifier = Modifier.fillMaxSize().padding(padding),
                        )
                    }

                    entry<VehicleEdit> {
                        VehicleSettingsRoute(modifier = Modifier.fillMaxSize().padding(padding))
                    }

                    entry<Subscriptions> {
                        SubscriptionsRoute(modifier = Modifier.fillMaxSize().padding(padding))
                    }

                    entry<Networks> {
                        NetworksRoute(modifier = Modifier.fillMaxSize().padding(padding))
                    }

                    entry<CarData> {
                        CarDataDebugRoute(modifier = Modifier.fillMaxSize().padding(padding))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

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

    // Drawer and sheets close before the stack pops — the order the old
    // hand-rolled hierarchy had. Registered after NavDisplay so this handler
    // wins while it is enabled; the page pops are NavDisplay's business.
    BackHandler(enabled = drawerState.isOpen || sheet != Sheet.NONE) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            else -> sheet = Sheet.NONE
        }
    }
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
