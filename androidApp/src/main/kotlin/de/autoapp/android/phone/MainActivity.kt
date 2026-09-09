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
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppSheet
import de.autoapp.android.phone.components.AppTopBar
import de.autoapp.android.phone.components.TopBarIcon
import de.autoapp.android.phone.theme.ChargeAheadTheme
import de.autoapp.shared.core.MapsHandoff
import de.autoapp.shared.core.PlannedStop
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
 * here is what is genuinely the app shell's: which page is showing, which
 * sheet is open, and the Android-only permission handshake. Navigation stays
 * the enum-page pattern until Navigation3 lands
 * (plans/technical-debts.md) — the flows are simple enough that a library
 * would only add ceremony today.
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

internal enum class Page { HOME, TRIP, STOP_DETAIL, GARAGE, ADD_CAR, VEHICLE_EDIT, SUBSCRIPTIONS, NETWORKS, CAR_DATA }

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

    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var detailStop by remember { mutableStateOf<PlannedStop?>(null) }

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
            TripEvent.PlanReady -> page = Page.TRIP
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

    // System back walks the same hierarchy the visible back arrows do —
    // without this, the first back gesture kills the whole activity.
    BackHandler(
        enabled = drawerState.isOpen || sheet != Sheet.NONE || page != Page.HOME,
    ) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            sheet != Sheet.NONE -> sheet = Sheet.NONE
            page == Page.STOP_DETAIL -> page = Page.TRIP
            page == Page.VEHICLE_EDIT -> page = Page.GARAGE
            page == Page.ADD_CAR -> page = Page.GARAGE
            else -> page = Page.HOME
        }
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
                    onOpen = { target -> scope.launch { drawerState.close(); page = target } },
                    onFilters = drawerViewModel::onFiltersChanged,
                    onLabelStyle = drawerViewModel::onLabelStyleChanged,
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (page != Page.HOME) {
                    AppTopBar(
                        title = when (page) {
                            Page.TRIP -> "→ ${planned?.plan?.destination?.name.orEmpty()}"
                            Page.STOP_DETAIL -> stringResource(R.string.detail_title)
                            Page.GARAGE -> stringResource(R.string.garage_title)
                            Page.ADD_CAR -> stringResource(R.string.garage_add_title)
                            Page.VEHICLE_EDIT -> stringResource(R.string.phone_settings_title)
                            Page.SUBSCRIPTIONS -> stringResource(R.string.subs_title)
                            Page.NETWORKS -> stringResource(R.string.phone_networks_title)
                            Page.CAR_DATA -> stringResource(R.string.cardata_title)
                            Page.HOME -> stringResource(R.string.app_name)
                        },
                        subtitle = when (page) {
                            Page.TRIP -> planned?.plan?.let {
                                pluralStringResource(R.plurals.trip_topbar_sub, it.stops.size, it.stops.size)
                            }

                            Page.STOP_DETAIL -> {
                                val plan = planned?.plan
                                val stop = detailStop
                                if (plan != null && stop != null && stop in plan.stops) {
                                    stringResource(R.string.detail_stop_x_of_y, plan.stops.indexOf(stop) + 1, plan.stops.size)
                                } else {
                                    null
                                }
                            }

                            Page.NETWORKS -> networksSummary(drawerUi.preferredNetworkCount)
                            else -> null
                        },
                        onBack = {
                            page = when (page) {
                                Page.STOP_DETAIL -> Page.TRIP
                                Page.VEHICLE_EDIT -> Page.GARAGE
                                Page.ADD_CAR -> Page.GARAGE
                                else -> Page.HOME
                            }
                        },
                        actions = {
                            if (page == Page.TRIP) {
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
            when (page) {
                Page.HOME -> HomeRoute(
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

                Page.TRIP -> planned?.let { trip ->
                    TripPlanScreen(
                        plan = trip.plan,
                        startPosition = trip.startPosition,
                        startSocPercent = trip.startSocPercent,
                        isSaved = trip.isSaved,
                        isEstimate = trip.isEstimate,
                        hasLocationPermission = hasPermission,
                        onOpenStop = { stop -> detailStop = stop; page = Page.STOP_DETAIL },
                        onSendToMaps = ::sendToMaps,
                        onToggleSave = { tripViewModel.toggleSaved(trip.plan.summaryLine()) },
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                } ?: run { page = Page.HOME }

                Page.STOP_DETAIL -> detailStop?.let { stop ->
                    StopDetailScreen(
                        stop = stop,
                        onSendToMaps = ::sendToMaps,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                } ?: run { page = Page.TRIP }

                Page.GARAGE -> GarageRoute(
                    onOpenAdvanced = { page = Page.VEHICLE_EDIT },
                    onOpenAdd = { page = Page.ADD_CAR },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

                Page.ADD_CAR -> AddCarRoute(
                    onAdded = { preset ->
                        page = Page.GARAGE
                        snackbar.show(scope, context.getString(R.string.garage_added, preset.name))
                    },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

                Page.VEHICLE_EDIT -> VehicleSettingsRoute(
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

                Page.SUBSCRIPTIONS -> SubscriptionsRoute(
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

                Page.CAR_DATA -> CarDataDebugRoute(
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

                Page.NETWORKS -> NetworksRoute(
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
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
}

/** Shown under a saved route's name. User-visible text, hence German. */
private fun TripPlan.summaryLine(): String =
    "${route.distanceKm.roundToInt()} km · ${stops.size} Stopps"

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
