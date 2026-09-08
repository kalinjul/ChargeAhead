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
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import de.autoapp.android.phone.components.AppSheet
import de.autoapp.android.phone.components.AppTopBar
import de.autoapp.android.phone.components.TopBarIcon
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.autoapp.android.ChargeStopsFeatureProvider
import de.autoapp.android.R
import de.autoapp.android.phone.theme.ChargeAheadTheme
import de.autoapp.shared.core.PlannedStop
import de.autoapp.shared.core.TripPlan
import de.autoapp.shared.core.TripPlanResult
import de.autoapp.shared.core.ChargeNowResult
import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.MapLabelStyle
import de.autoapp.shared.domain.Reachability
import de.autoapp.shared.domain.distanceKmTo
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.SavedRoute
import de.autoapp.shared.domain.SoCSourceKind
import de.autoapp.shared.PlanningFeature
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The phone app: map-first, the flows from the design mockup (docs/mockup) —
 * plan a route with charging stops, "charge now", garage, subscriptions,
 * filters, saved routes. Navigation stays the enum-page pattern from before;
 * the flows are simple enough that a navigation library would only add
 * ceremony.
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
    val settings = remember { ChargeStopsFeatureProvider.settingsStore(context) }
    val feature = remember { ChargeStopsFeatureProvider.create(context) }
    val planning = remember { feature.planning }

    val state by feature.state.collectAsState()
    val vehicle by settings.vehicle.collectAsState(initial = null)
    val vehicles by settings.vehicles.collectAsState(initial = emptyList())
    val manualSoc by settings.manualSocPercent.collectAsState(initial = null)
    val networks by settings.networks.collectAsState(initial = NetworkPreferences())
    val filters by settings.chargeFilters.collectAsState(initial = ChargeFilters())
    val activeTariffs by settings.activeTariffIds.collectAsState(initial = emptySet())
    val savedRoutes by settings.savedRoutes.collectAsState(initial = emptyList())
    val mapLabelStyle by settings.mapLabelStyle.collectAsState(initial = MapLabelStyle.PRICE)
    val recentDestinations by settings.recentDestinations.collectAsState(initial = emptyList())
    val diagnostics by settings.socDiagnostics.collectAsState(initial = null)
    val carDebugData by settings.carDebugData.collectAsState(initial = emptyList())

    val filtersCustomized = !filters.isDefault || networks.isActive
    val networksSummary = if (networks.isActive) {
        stringResource(R.string.drawer_networks_selected, networks.preferredOperators.size)
    } else {
        stringResource(R.string.drawer_networks_all)
    }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var page by remember { mutableStateOf(Page.HOME) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var tripPlan by remember { mutableStateOf<TripPlan?>(null) }
    var planningInProgress by remember { mutableStateOf(false) }
    var detailStop by remember { mutableStateOf<PlannedStop?>(null) }
    var chargeNow by remember { mutableStateOf<ChargeNowResult?>(null) }
    var chargeNowLoading by remember { mutableStateOf(false) }
    var corridorStop by remember { mutableStateOf<de.autoapp.shared.domain.ChargeStop?>(null) }
    var mapChargers by remember { mutableStateOf<List<de.autoapp.shared.MapCharger>>(emptyList()) }
    var mapBelowZoom by remember { mutableStateOf(false) }

    var hasPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasPermission = results.values.any { it }
        if (hasPermission) feature.start()
    }

    DisposableEffect(feature) {
        if (hasPermission) feature.start()
        onDispose { feature.close() }
    }

    fun sendToMaps(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            scope.launch { snackbar.showSnackbar(context.getString(R.string.trip_maps_sent)) }
        } catch (notFound: ActivityNotFoundException) {
            Toast.makeText(context, R.string.phone_detail_no_navigation, Toast.LENGTH_LONG).show()
        }
    }

    fun planTo(destination: Destination, socPercent: Double) {
        val from = state.position ?: return
        sheet = Sheet.NONE
        planningInProgress = true
        scope.launch {
            // Persisted, not just used: the garage and the car UI read the
            // same value, and the next plan starts from it.
            settings.setManualSocPercent(socPercent)
            feature.setDestination(destination)
            when (val result = planning?.planTrip(from, destination, socOverridePercent = socPercent)) {
                is TripPlanResult.Planned -> {
                    tripPlan = result.plan
                    page = Page.TRIP
                }

                is TripPlanResult.NoVehicle ->
                    snackbar.showSnackbar(context.getString(R.string.plan_vehicle_missing))

                is TripPlanResult.NoChargerInReach ->
                    snackbar.showSnackbar(
                        context.getString(R.string.plan_failed_no_charger, result.afterKm.roundToInt()),
                    )

                is TripPlanResult.NoRoute, null ->
                    snackbar.showSnackbar(context.getString(R.string.plan_failed_no_route))
            }
            planningInProgress = false
        }
    }

    fun openChargeNow() {
        val position = state.position
        sheet = Sheet.CHARGE_NOW
        if (position == null) {
            chargeNow = null
            return
        }
        chargeNowLoading = true
        scope.launch {
            chargeNow = planning?.chargeNow(position)
            chargeNowLoading = false
        }
    }

    val currentSaved = tripPlan?.let { plan ->
        savedRoutes.firstOrNull { it.destination.position == plan.destination.position }
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
                    vehicleName = vehicle?.displayName,
                    activeTariffCount = activeTariffs.size,
                    networksSummary = networksSummary,
                    filters = filters,
                    labelStyle = mapLabelStyle,
                    // Close first, then navigate: the page swap disposes the
                    // map and its jank freezes a concurrently running drawer
                    // animation — the drawer then just hangs there, open.
                    onOpen = { target -> scope.launch { drawerState.close(); page = target } },
                    onFilters = { updated -> scope.launch { settings.setChargeFilters(updated) } },
                    onLabelStyle = { style -> scope.launch { settings.setMapLabelStyle(style) } },
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
                            Page.TRIP -> "→ ${tripPlan?.destination?.name.orEmpty()}"
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
                            Page.TRIP -> tripPlan?.let { pluralStringResource(R.plurals.trip_topbar_sub, it.stops.size, it.stops.size) }
                            Page.STOP_DETAIL -> {
                                val plan = tripPlan
                                val stop = detailStop
                                if (plan != null && stop != null && stop in plan.stops) {
                                    stringResource(R.string.detail_stop_x_of_y, plan.stops.indexOf(stop) + 1, plan.stops.size)
                                } else {
                                    null
                                }
                            }
                            Page.NETWORKS -> networksSummary
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
                                    badge = filtersCustomized,
                                )
                            }
                        },
                    )
                }
            },
        ) { padding ->
            when (page) {
                Page.HOME -> HomeScreen(
                    state = state,
                    hasPermission = hasPermission,
                    planningInProgress = planningInProgress,
                    chargers = mapChargers,
                    belowZoom = mapBelowZoom,
                    filtersCustomized = filtersCustomized,
                    onViewportChanged = { viewport ->
                        if (viewport == null) {
                            mapBelowZoom = true
                            mapChargers = emptyList()
                        } else {
                            mapBelowZoom = false
                            scope.launch {
                                mapChargers = planning?.chargersIn(viewport).orEmpty()
                            }
                        }
                    },
                    onChargerTapped = { charger ->
                        // The corridor dialog fits: same site type, and
                        // reachability honestly UNKNOWN — the map doesn't rate.
                        corridorStop = de.autoapp.shared.domain.ChargeStop(
                            site = charger.site,
                            distanceKm = state.position?.distanceKmTo(charger.site.position) ?: 0.0,
                            reachability = Reachability.UNKNOWN,
                            socOnArrivalPercent = null,
                        )
                    },
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onMenu = { scope.launch { drawerState.open() } },
                    onPlan = { sheet = Sheet.PLAN },
                    onChargeNow = { openChargeNow() },
                    onRoutes = { sheet = Sheet.ROUTES },
                    // No scaffold padding: the map draws under the (now
                    // dark-iconed) status bar, like every maps app.
                    modifier = Modifier.fillMaxSize(),
                )

                Page.TRIP -> tripPlan?.let { plan ->
                    TripPlanScreen(
                        plan = plan,
                        startPosition = state.position,
                        startSocPercent = manualSoc,
                        isSaved = currentSaved != null,
                        isEstimate = plan.stops.any { it.quote.isEstimate },
                        hasLocationPermission = hasPermission,
                        onOpenStop = { detailStop = it; page = Page.STOP_DETAIL },
                        onSendToMaps = ::sendToMaps,
                        onToggleSave = {
                            scope.launch {
                                val existing = currentSaved
                                if (existing != null) {
                                    settings.removeSavedRoute(existing.id)
                                    snackbar.showSnackbar(context.getString(R.string.trip_unsaved))
                                } else {
                                    settings.saveRoute(plan.toSavedRoute())
                                    snackbar.showSnackbar(context.getString(R.string.trip_saved))
                                }
                            }
                        },
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

                Page.GARAGE -> GarageScreen(
                    vehicles = vehicles,
                    selected = vehicle,
                    socPercent = manualSoc,
                    socFromCar = state.socSource == SoCSourceKind.CAR_HARDWARE,
                    onSelect = { scope.launch { settings.setVehicle(it) } },
                    onRemove = { scope.launch { settings.removeVehicle(it) } },
                    onSocChange = { scope.launch { settings.setManualSocPercent(it) } },
                    onOpenAdvanced = { page = Page.VEHICLE_EDIT },
                    onOpenAdd = { page = Page.ADD_CAR },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

                Page.ADD_CAR -> AddCarScreen(
                    owned = vehicles.map { it.displayName }.toSet(),
                    onAdd = { preset ->
                        scope.launch {
                            settings.setVehicle(preset.toProfile())
                            snackbar.showSnackbar(context.getString(R.string.garage_added, preset.name))
                        }
                        page = Page.GARAGE
                    },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

                Page.VEHICLE_EDIT -> VehicleSettingsScreen(
                    vehicle = vehicle,
                    socPercent = manualSoc,
                    socFromCar = state.socSource == SoCSourceKind.CAR_HARDWARE,
                    onVehicleChange = { scope.launch { settings.setVehicle(it) } },
                    onSocChange = { scope.launch { settings.setManualSocPercent(it) } },
                    diagnostics = diagnostics,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

                Page.SUBSCRIPTIONS -> SubscriptionsScreen(
                    activeIds = activeTariffs,
                    onChange = { scope.launch { settings.setActiveTariffIds(it) } },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

                Page.CAR_DATA -> CarDataDebugScreen(
                    points = carDebugData,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

                Page.NETWORKS -> NetworkSettingsScreen(
                    preferences = networks,
                    available = state.availableOperators,
                    onChange = { scope.launch { settings.setNetworks(it) } },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        }
    }

    corridorStop?.let { stop ->
        ChargeStopDetailDialog(stop = stop, onDismiss = { corridorStop = null })
    }

    when (sheet) {
        Sheet.NONE -> Unit

        Sheet.PLAN -> AppSheet(onDismissRequest = { sheet = Sheet.NONE }) {
            PlanSheetContent(
                recent = recentDestinations,
                vehicleName = vehicle?.displayName,
                initialSocPercent = manualSoc,
                from = state.position,
                onSearch = { query ->
                    runCatching { feature.searchDestinations(query) }.getOrNull()
                },
                onPlan = ::planTo,
            )
        }

        Sheet.CHARGE_NOW -> AppSheet(onDismissRequest = { sheet = Sheet.NONE }) {
            if (state.position == null) {
                Text(
                    stringResource(R.string.home_no_position),
                    modifier = Modifier.navigationBarsPadding().padding(24.dp),
                )
            } else {
                ChargeNowSheetContent(
                    result = chargeNow,
                    loading = chargeNowLoading,
                    onNavigate = { candidate ->
                        sendToMaps(de.autoapp.shared.core.MapsHandoff.navigateUrl(candidate.site.position))
                    },
                )
            }
        }

        Sheet.ROUTES -> AppSheet(onDismissRequest = { sheet = Sheet.NONE }) {
            RoutesSheetContent(
                saved = savedRoutes,
                recent = recentDestinations,
                // Reopening a route from the list keeps the stored charge
                // level; adjusting it is what the plan sheet is for.
                onOpen = { destination ->
                    sheet = Sheet.NONE
                    planTo(destination, manualSoc ?: PlanningFeature.DEFAULT_ASSUMED_SOC_PERCENT)
                },
                onRename = { route, name -> scope.launch { settings.renameSavedRoute(route.id, name) } },
                onDelete = { route -> scope.launch { settings.removeSavedRoute(route.id) } },
                onFavorite = { destination ->
                    scope.launch {
                        settings.saveRoute(
                            SavedRoute(
                                id = destination.routeId(),
                                name = destination.name,
                                destination = destination,
                            ),
                        )
                    }
                },
            )
        }
    }
}

private fun TripPlan.toSavedRoute(): SavedRoute = SavedRoute(
    id = destination.routeId(),
    name = destination.name,
    destination = destination,
    summary = "${route.distanceKm.roundToInt()} km · ${stops.size} Stopps",
)

private fun Destination.routeId(): String = "dest:${position.lat},${position.lon}"

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
