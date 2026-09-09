package de.autoapp.android.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.autoapp.android.R
import de.autoapp.shared.MapCharger
import de.autoapp.shared.domain.BoundingBox
import de.autoapp.shared.ui.HomeUiState
import de.autoapp.shared.ui.HomeViewModel

/**
 * The map screen with its state holder attached. Everything below this
 * function is stateless and takes what it draws as parameters — that is what
 * keeps [HomeScreen] previewable and testable without a location provider.
 */
@Composable
fun HomeRoute(
    hasPermission: Boolean,
    planningInProgress: Boolean,
    onRequestPermission: () -> Unit,
    onMenu: () -> Unit,
    onPlan: () -> Unit,
    onChargeNow: () -> Unit,
    onRoutes: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The pipeline may only run once the permission is there; starting it
    // twice is a no-op, so re-running this on every grant is harmless.
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.onLocationPermissionGranted()
    }

    HomeScreen(
        uiState = uiState,
        hasPermission = hasPermission,
        planningInProgress = planningInProgress,
        onViewportChanged = viewModel::onViewportChanged,
        onChargerTapped = viewModel::onChargerSelected,
        onRequestPermission = onRequestPermission,
        onMenu = onMenu,
        onPlan = onPlan,
        onChargeNow = onChargeNow,
        onRoutes = onRoutes,
        modifier = modifier,
    )

    uiState.selectedStop?.let { stop ->
        ChargeStopDetailSheet(stop = stop, onDismiss = viewModel::onSelectedStopDismissed)
    }
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    hasPermission: Boolean,
    planningInProgress: Boolean,
    onViewportChanged: (BoundingBox?) -> Unit,
    onChargerTapped: (MapCharger) -> Unit,
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
                position = uiState.position,
                chargers = uiState.chargers,
                hasLocationPermission = hasPermission,
                onViewportChanged = onViewportChanged,
                onChargerTapped = onChargerTapped,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MapCanvas(
                center = uiState.position,
                pins = uiState.stops.map { MapPin(it.site.position, operatorColor(it.site.operator)) },
                ownPosition = uiState.position,
                radiusKm = 25.0,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)) {
            Surface(
                onClick = onMenu,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_menu),
                        contentDescription = stringResource(R.string.home_menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (uiState.filtersCustomized) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(11.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
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
            if (hasGoogleMapsKey && uiState.belowMinZoom) {
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
            if (uiState.isDemo) {
                // Invented charging sites must be labeled — see AGENTS.md.
                Text(
                    stringResource(R.string.phone_demo_notice),
                    style = MaterialTheme.typography.bodySmall,
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            HomePill(
                text = stringResource(R.string.home_pill_plan),
                icon = painterResource(R.drawable.ic_route),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = onPlan,
            )
            HomePill(
                text = stringResource(R.string.home_pill_charge_now),
                icon = painterResource(R.drawable.ic_battery),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                iconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onChargeNow,
            )
            HomePill(
                text = null,
                icon = painterResource(R.drawable.ic_heart),
                contentDescription = stringResource(R.string.home_routes),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                iconTint = MaterialTheme.colorScheme.error,
                onClick = onRoutes,
            )
        }
    }
}

/** The mockup's `.pill`: fully round, floating, 15sp/700. */
@Composable
private fun HomePill(
    text: String?,
    icon: Painter,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    iconTint: Color = contentColor,
    contentDescription: String? = null,
) {
    Surface(onClick = onClick, shape = CircleShape, color = containerColor, contentColor = contentColor, shadowElevation = 6.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.padding(horizontal = if (text != null) 21.dp else 15.dp, vertical = 14.dp),
        ) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(18.dp))
            text?.let { Text(it, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)) }
        }
    }
}
