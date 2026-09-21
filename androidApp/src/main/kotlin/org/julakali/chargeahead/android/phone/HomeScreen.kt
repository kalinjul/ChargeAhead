package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import kotlinx.coroutines.launch
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.MapCharger
import org.julakali.chargeahead.shared.ui.HomeUiState
import org.julakali.chargeahead.shared.ui.HomeViewModel
import org.koin.androidx.compose.koinViewModel

enum class HomeMode { BROWSING, SEARCHING, TRIP }

/** The map screen with its state holder attached. */
@Composable
fun HomeRoute(
    hasPermission: Boolean,
    planningInProgress: Boolean,
    mode: HomeMode,
    route: RouteOverlay?,
    /** The trip sheet's peek, so the route fits above it. */
    mapBottomInset: Dp,
    onRequestPermission: () -> Unit,
    /** The location button with nothing to center on. Owned by the activity. */
    onLocate: () -> Unit,
    onSettings: () -> Unit,
    onChargeNow: () -> Unit,
    onRoutes: () -> Unit,
    /** A tap on the map while the results panel is open. */
    onDismissSearch: () -> Unit,
    /** A numbered route marker was tapped, 1-based. */
    onStopTapped: (Int) -> Unit,
    /** Arrival and departure for a selected site that is a planned stop. */
    tripLineFor: (ChargeStop) -> String?,
    /** Search bar or destination header. */
    topBar: @Composable () -> Unit,
    /** Results while searching. */
    topPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The pipeline may only run once the permission is there.
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.onLocationPermissionGranted()
    }

    HomeScreen(
        uiState = uiState,
        hasPermission = hasPermission,
        planningInProgress = planningInProgress,
        mode = mode,
        route = route,
        mapBottomInset = mapBottomInset,
        onViewportChanged = viewModel::onViewportChanged,
        onChargerTapped = viewModel::onChargerSelected,
        onRequestPermission = onRequestPermission,
        onLocate = onLocate,
        onSettings = onSettings,
        onChargeNow = onChargeNow,
        onRoutes = onRoutes,
        onDismissSearch = onDismissSearch,
        onStopTapped = onStopTapped,
        topBar = topBar,
        topPanel = topPanel,
        modifier = modifier,
    )

    uiState.selectedStop?.let { stop ->
        ChargeStopDetailSheet(
            stop = stop,
            live = uiState.selectedStopLive,
            onDismiss = viewModel::onSelectedStopDismissed,
            tripLine = tripLineFor(stop),
        )
    }
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    hasPermission: Boolean,
    planningInProgress: Boolean,
    mode: HomeMode,
    route: RouteOverlay?,
    mapBottomInset: Dp,
    onViewportChanged: (BoundingBox?) -> Unit,
    onChargerTapped: (MapCharger) -> Unit,
    onRequestPermission: () -> Unit,
    onLocate: () -> Unit,
    onSettings: () -> Unit,
    onChargeNow: () -> Unit,
    onRoutes: () -> Unit,
    onDismissSearch: () -> Unit,
    onStopTapped: (Int) -> Unit,
    topBar: @Composable () -> Unit,
    topPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val camera = rememberHomeCamera(uiState.position)
    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        if (hasGoogleMapsKey) {
            HomeGoogleMap(
                position = uiState.position,
                // The route replaces the browsing markers.
                chargers = if (mode == HomeMode.TRIP) emptyList() else uiState.chargers,
                route = route,
                bottomInset = mapBottomInset,
                hasLocationPermission = hasPermission,
                cameraPositionState = camera,
                onViewportChanged = onViewportChanged,
                onChargerTapped = onChargerTapped,
                onStopTapped = onStopTapped,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MissingMapsKeyNotice(Modifier.fillMaxSize())
        }

        // While the panel is open the map only takes a dismissing tap.
        if (mode == HomeMode.SEARCHING) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onDismissSearch() } },
            )
        }

        // Bar + settings on one line, the map controls hanging under the settings icon.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { topBar() }
                RoundIconButton(onClick = onSettings, badge = uiState.filtersCustomized) {
                    RoundIcon(painterResource(R.drawable.ic_filter), stringResource(R.string.home_settings))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        mode == HomeMode.SEARCHING -> topPanel()
                        // Location is running and getting nowhere.
                        uiState.locationUnavailable -> HintChip(
                            stringResource(R.string.phone_status_location_unavailable),
                            MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val bearing = camera.position.bearing
                    if (bearing != 0f) {
                        RoundIconButton(onClick = {
                            scope.launch {
                                camera.animate(
                                    CameraUpdateFactory.newCameraPosition(
                                        CameraPosition.Builder(camera.position).bearing(0f).tilt(0f).build(),
                                    ),
                                )
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Navigation,
                                contentDescription = stringResource(R.string.map_compass),
                                tint = Color(0xFFD93025),
                                // Counter-rotated to point north. graphicsLayer, so only the draw is invalidated.
                                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = -camera.position.bearing },
                            )
                        }
                    }
                    RoundIconButton(onClick = {
                        // With a position, center on it; otherwise ask for a fix.
                        val target = uiState.position
                        if (target == null) {
                            onLocate()
                        } else {
                            scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), HOME_ZOOM)) }
                        }
                    }) {
                        if (uiState.searchingLocation) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = stringResource(R.string.map_my_location),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    // A charger source is being asked over the network.
                    if (uiState.loadingSites) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        if (!hasPermission) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.phone_permission_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRequestPermission) {
                        Text(stringResource(R.string.phone_permission_action))
                    }
                }
            }
        }

        if (planningInProgress) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.plan_planning))
                }
            }
        }

        if (mode == HomeMode.BROWSING) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            ) {
                if (uiState.belowMinZoom) HintChip(stringResource(R.string.map_zoom_hint))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HomePill(
                        text = stringResource(R.string.home_pill_charge_now),
                        icon = painterResource(R.drawable.ic_battery),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = onChargeNow,
                    )
                    HomePill(
                        text = stringResource(R.string.home_pill_favorites),
                        icon = painterResource(R.drawable.ic_heart),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = onRoutes,
                    )
                }
            }
        }
    }
}
