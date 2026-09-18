package org.julakali.chargeahead.android.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import org.julakali.chargeahead.android.BuildConfig
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.shared.domain.ChargeSpeed
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MapCharger
import org.julakali.chargeahead.shared.domain.OperatorShortName

/**
 * The Google map. Without a `googleMapsApiKey` in `local.properties` the
 * callers fall back to [MapCanvas].
 */
val hasGoogleMapsKey: Boolean get() = BuildConfig.HAS_GOOGLE_MAPS_KEY

private fun LatLon.toLatLng() = LatLng(lat, lon)

/** A charger with its icon and position resolved. */
private class ChargerMarker(
    val charger: MapCharger,
    val position: LatLng,
    val icon: BitmapDescriptor,
)

/**
 * Home map: viewport-driven. The map reports every settled camera position
 * upward (`null` below [MIN_CHARGER_ZOOM]); the caller passes the chargers back down.
 */
@Composable
fun HomeGoogleMap(
    position: LatLon?,
    chargers: List<MapCharger>,
    hasLocationPermission: Boolean,
    onViewportChanged: (org.julakali.chargeahead.shared.domain.BoundingBox?) -> Unit,
    onChargerTapped: (MapCharger) -> Unit,
    onLocate: () -> Unit,
    searchingLocation: Boolean,
    loadingSites: Boolean,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(
            (position ?: FALLBACK_CENTER).toLatLng(),
            HOME_ZOOM,
        )
    }

    // Load once the camera settles. Also fires for the initial position.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) return@LaunchedEffect
        kotlinx.coroutines.delay(350)
        if (cameraPositionState.position.zoom < MIN_CHARGER_ZOOM) {
            onViewportChanged(null)
            return@LaunchedEffect
        }
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        onViewportChanged(
            org.julakali.chargeahead.shared.domain.BoundingBox(
                south = bounds.southwest.latitude,
                west = bounds.southwest.longitude,
                north = bounds.northeast.latitude,
                east = bounds.northeast.longitude,
            ),
        )
    }

    // Follow the first fix, then leave the camera to the user.
    var followedFirstFix by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(position != null) {
        val target = position ?: return@LaunchedEffect
        if (!followedFirstFix) {
            followedFirstFix = true
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(target.toLatLng(), HOME_ZOOM),
            )
        }
    }

    val scope = rememberCoroutineScope()

    // Each distinct pill (speed, operator label, availability) is rasterized once and reused.
    val density = LocalDensity.current
    val iconCache = remember { mutableMapOf<PillKey, BitmapDescriptor>() }
    val outOfOrderText = stringResource(R.string.map_out_of_order)

    // Built once per charger-list change, not per recomposition.
    val markers = remember(chargers, density, outOfOrderText) {
        chargers.map { charger ->
            val pillKey = PillKey(
                speed = ChargeSpeed.of(charger.maxPowerKw),
                label = OperatorShortName.of(charger.site.operator),
                availability = charger.availability,
                outOfOrderText = outOfOrderText,
            )
            ChargerMarker(
                charger = charger,
                position = charger.site.position.toLatLng(),
                icon = iconCache.getOrPut(pillKey) { markerPillDescriptor(density, pillKey) },
            )
        }
    }

    val loadingDescription = stringResource(R.string.map_loading_sites)

    Box(modifier = modifier) {
        GoogleMap(
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            // The SDK's own buttons would sit inside the status bar; ours replace them.
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            markers.forEach { marker ->
                key(marker.charger.site.id) {
                    Marker(
                        state = rememberMarkerState(position = marker.position),
                        icon = marker.icon,
                        title = marker.charger.site.name,
                        anchor = Offset(0.5f, 0.5f),
                        onClick = { onChargerTapped(marker.charger); true },
                    )
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            SmallFloatingActionButton(
                // With a position, center on it; otherwise ask for a fix.
                onClick = {
                    val target = position
                    if (target == null) {
                        onLocate()
                    } else {
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(target.toLatLng(), HOME_ZOOM),
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                // Spinning crosshair while location is running but has nothing yet.
                if (searchingLocation) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = stringResource(R.string.map_my_location),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder(cameraPositionState.position)
                                    .bearing(0f)
                                    .tilt(0f)
                                    .build(),
                            ),
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = stringResource(R.string.map_compass),
                    tint = Color(0xFFD93025),
                    // Counter-rotated to point north. graphicsLayer, so only the draw is invalidated.
                    modifier = Modifier.graphicsLayer { rotationZ = -cameraPositionState.position.bearing },
                )
            }
            // A charger source is being asked over the network.
            AnimatedVisibility(
                visible = loadingSites,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .semantics { contentDescription = loadingDescription },
                )
            }
        }
    }
}

/** Circular disc with a white ring — the planned-stop badges on the trip map. */
@Composable
private fun ChargeBadge(color: Color, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape),
    ) {
        content()
    }
}

/** Trip map: the planned route with its charging stops, framed to fit. */
@Composable
fun TripGoogleMap(
    routePoints: List<LatLon>,
    stops: List<Pair<Int, LatLon>>,
    destination: LatLon,
    hasLocationPermission: Boolean,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState()

    // Converted once per route change.
    val routeLatLngs = remember(routePoints) { routePoints.map { it.toLatLng() } }

    LaunchedEffect(routeLatLngs) {
        if (routeLatLngs.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.builder()
            .apply { routeLatLngs.forEach { include(it) } }
            .build()
        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING_PX))
    }

    GoogleMap(
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
        modifier = modifier,
    ) {
        if (routeLatLngs.size >= 2) {
            Polyline(
                points = routeLatLngs,
                color = androidx.compose.ui.graphics.Color(0xFF1A73E8),
                width = 14f,
            )
        }
        stops.forEach { (index, position) ->
            key(index) {
                MarkerComposable(
                    keys = arrayOf(index),
                    state = rememberMarkerState(position = position.toLatLng()),
                    anchor = Offset(0.5f, 0.5f),
                ) {
                    ChargeBadge(color = Color(0xFF1A73E8)) {
                        Text(
                            text = "$index",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        Marker(state = rememberMarkerState(position = destination.toLatLng()))
    }
}

/** Frankfurt, shown before the first fix. */
private val FALLBACK_CENTER = LatLon(50.11, 8.68)
private const val HOME_ZOOM = 11f

/** Below this, no chargers load. */
const val MIN_CHARGER_ZOOM = 10f
private const val BOUNDS_PADDING_PX = 120

