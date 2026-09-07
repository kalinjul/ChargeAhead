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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
        // Explicit edge-to-edge: with a light theme this also flips the
        // status-bar icons to dark — without it they stay white on our white
        // surfaces and vanish.
        enableEdgeToEdge()
        setContent {
            ChargeAheadTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhoneApp()
                }
            }
        }
    }
}

private enum class Page { HOME, TRIP, STOP_DETAIL, GARAGE, VEHICLE_EDIT, SUBSCRIPTIONS, NETWORKS, CAR_DATA }

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
            else -> page = Page.HOME
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the burger: the edge swipe fights the map's pan
        // gesture and wins far too often.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    vehicleName = vehicle?.displayName,
                    activeTariffCount = activeTariffs.size,
                    filters = filters,
                    labelStyle = mapLabelStyle,
                    onOpen = { target -> page = target; scope.launch { drawerState.close() } },
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
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(
                                    when (page) {
                                        Page.TRIP -> R.string.trip_title
                                        Page.STOP_DETAIL -> R.string.detail_title
                                        Page.GARAGE -> R.string.garage_title
                                        Page.VEHICLE_EDIT -> R.string.phone_settings_title
                                        Page.SUBSCRIPTIONS -> R.string.subs_title
                                        Page.NETWORKS -> R.string.phone_networks_title
                                        Page.CAR_DATA -> R.string.cardata_title
                                        Page.HOME -> R.string.app_name
                                    },
                                ),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                page = when (page) {
                                    Page.STOP_DETAIL -> Page.TRIP
                                    Page.VEHICLE_EDIT -> Page.GARAGE
                                    else -> Page.HOME
                                }
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_back),
                                    contentDescription = stringResource(R.string.common_back),
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
                    snackbar = snackbar,
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

        Sheet.PLAN -> ModalBottomSheet(onDismissRequest = { sheet = Sheet.NONE }) {
            PlanSheetContent(
                recent = recentDestinations,
                vehicleName = vehicle?.displayName,
                initialSocPercent = manualSoc,
                onSearch = { query ->
                    runCatching { feature.searchDestinations(query) }.getOrNull()
                },
                onPlan = ::planTo,
            )
        }

        Sheet.CHARGE_NOW -> ModalBottomSheet(onDismissRequest = { sheet = Sheet.NONE }) {
            if (state.position == null) {
                Text(
                    stringResource(R.string.home_no_position),
                    modifier = Modifier.padding(24.dp),
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

        Sheet.ROUTES -> ModalBottomSheet(onDismissRequest = { sheet = Sheet.NONE }) {
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

@Composable
private fun HomeScreen(
    state: de.autoapp.shared.ChargeStopsState,
    hasPermission: Boolean,
    planningInProgress: Boolean,
    chargers: List<de.autoapp.shared.MapCharger>,
    belowZoom: Boolean,
    onViewportChanged: (de.autoapp.shared.domain.BoundingBox?) -> Unit,
    onChargerTapped: (de.autoapp.shared.MapCharger) -> Unit,
    onRequestPermission: () -> Unit,
    onMenu: () -> Unit,
    onPlan: () -> Unit,
    onChargeNow: () -> Unit,
    onRoutes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (hasGoogleMapsKey) {
            HomeGoogleMap(
                position = state.position,
                chargers = chargers,
                hasLocationPermission = hasPermission,
                onViewportChanged = onViewportChanged,
                onChargerTapped = onChargerTapped,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MapCanvas(
                center = state.position,
                pins = state.stops.map { MapPin(it.site.position, operatorColor(it.site.operator)) },
                ownPosition = state.position,
                radiusKm = 25.0,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)) {
            FloatingActionButton(
                onClick = onMenu,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = stringResource(R.string.home_menu),
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasGoogleMapsKey) {
                Text(
                    stringResource(R.string.home_map_placeholder),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasGoogleMapsKey && belowZoom) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        stringResource(R.string.map_zoom_hint),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            if (state.isDemo) {
                // Invented charging sites must be labeled — see AGENTS.md.
                Text(
                    stringResource(R.string.phone_demo_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (!hasPermission) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.phone_permission_message))
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.phone_permission_action))
                }
            }
        }

        if (planningInProgress) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                Text(stringResource(R.string.plan_planning))
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = onPlan,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = {
                    Icon(painter = painterResource(R.drawable.ic_route), contentDescription = null)
                },
                text = { Text(stringResource(R.string.home_pill_plan)) },
            )
            ExtendedFloatingActionButton(
                onClick = onChargeNow,
                containerColor = MaterialTheme.colorScheme.surface,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_battery),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                },
                text = { Text(stringResource(R.string.home_pill_charge_now)) },
            )
            FloatingActionButton(
                onClick = onRoutes,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_heart),
                    contentDescription = stringResource(R.string.home_routes),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerContent(
    vehicleName: String?,
    activeTariffCount: Int,
    filters: ChargeFilters,
    labelStyle: MapLabelStyle,
    onOpen: (Page) -> Unit,
    onFilters: (ChargeFilters) -> Unit,
    onLabelStyle: (MapLabelStyle) -> Unit,
) {
    Column(
        modifier = Modifier
            // The drawer draws edge-to-edge; without the inset its header
            // sits under the (white-on-white) status bar.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)

        Text(stringResource(R.string.drawer_preferences), style = MaterialTheme.typography.titleSmall)
        NavigationDrawerItem(
            label = {
                Column {
                    Text(stringResource(R.string.drawer_car))
                    Text(
                        vehicleName ?: stringResource(R.string.drawer_car_none),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            selected = false,
            onClick = { onOpen(Page.GARAGE) },
        )
        NavigationDrawerItem(
            label = {
                Column {
                    Text(stringResource(R.string.drawer_subscriptions))
                    Text(
                        stringResource(R.string.drawer_subs_count, activeTariffCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            selected = false,
            onClick = { onOpen(Page.SUBSCRIPTIONS) },
        )

        HorizontalDivider()
        Text(stringResource(R.string.drawer_filters), style = MaterialTheme.typography.titleSmall)
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_networks)) },
            selected = false,
            onClick = { onOpen(Page.NETWORKS) },
        )

        Text(stringResource(R.string.drawer_min_power), style = MaterialTheme.typography.bodyMedium)
        val powerSteps = listOf(50.0, 150.0, 300.0)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            powerSteps.forEachIndexed { index, step ->
                SegmentedButton(
                    selected = filters.minPowerKw == step,
                    onClick = { onFilters(filters.copy(minPowerKw = step)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = powerSteps.size),
                ) {
                    Text("${step.roundToInt()} kW")
                }
            }
        }

        Text(
            stringResource(R.string.drawer_max_price, filters.maxPriceEuroPerKwh.twoDecimals()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = filters.maxPriceEuroPerKwh.toFloat(),
            onValueChange = { onFilters(filters.copy(maxPriceEuroPerKwh = (it * 100).roundToInt() / 100.0)) },
            valueRange = 0.4f..1.0f,
        )

        Text(
            stringResource(R.string.drawer_max_distance, filters.maxDistanceKm.oneDecimal()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = filters.maxDistanceKm.toFloat(),
            onValueChange = { onFilters(filters.copy(maxDistanceKm = (it * 2).roundToInt() / 2.0)) },
            valueRange = 1f..10f,
        )

        Text(stringResource(R.string.drawer_map_label), style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = labelStyle == MapLabelStyle.PRICE,
                onClick = { onLabelStyle(MapLabelStyle.PRICE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.drawer_label_price)) }
            SegmentedButton(
                selected = labelStyle == MapLabelStyle.FREE_CHARGERS,
                onClick = {},
                enabled = false,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.drawer_label_free)) }
        }
        Text(
            stringResource(R.string.drawer_label_free_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        Text(stringResource(R.string.drawer_debug), style = MaterialTheme.typography.titleSmall)
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_cardata)) },
            selected = false,
            onClick = { onOpen(Page.CAR_DATA) },
        )

        Text(
            stringResource(R.string.drawer_availability_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
