package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.delay
import org.julakali.chargeahead.android.phone.BuildConfig
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MapCharger

/** Without a `googleMapsApiKey` in `local.properties` the callers show [MissingMapsKeyNotice] instead of a map. */
val hasGoogleMapsKey: Boolean get() = BuildConfig.HAS_GOOGLE_MAPS_KEY

@Composable
fun MissingMapsKeyNotice(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.map_missing_key),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

fun LatLon.toLatLng() = LatLng(lat, lon)

/** What the trip draws over the browsing map. */
data class RouteOverlay(
    val points: List<LatLon>,
    val stops: List<RouteStop>,
    val destination: LatLon,
)

/** A numbered stop marker; the operator picks its colour, like in the list. */
data class RouteStop(val index: Int, val position: LatLon, val operator: String?)

/** The home camera, owned by the screen so its controls can drive it. */
@Composable
fun rememberHomeCamera(position: LatLon?): CameraPositionState = rememberCameraPositionState {
    this.position = CameraPosition.fromLatLngZoom((position ?: FALLBACK_CENTER).toLatLng(), HOME_ZOOM)
}

/**
 * Home map: viewport-driven. The map reports every settled camera position
 * upward (`null` below [MIN_CHARGER_ZOOM]); the caller passes the chargers back down.
 */
@Composable
fun HomeGoogleMap(
    position: LatLon?,
    chargers: List<MapCharger>,
    route: RouteOverlay?,
    /** How much of the map's bottom the trip sheet covers. */
    bottomInset: Dp,
    hasLocationPermission: Boolean,
    cameraPositionState: CameraPositionState,
    onViewportChanged: (BoundingBox?) -> Unit,
    onChargerTapped: (MapCharger) -> Unit,
    /** A numbered route marker, by its 1-based index. */
    onStopTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No projection to read a viewport from until the map itself is up, so the
    // first run waits for it — otherwise nothing loads until the driver pans.
    var mapLoaded by remember { mutableStateOf(false) }

    // Load once the camera settles. Also fires for the initial position.
    LaunchedEffect(mapLoaded, cameraPositionState.isMoving) {
        if (!mapLoaded || cameraPositionState.isMoving) return@LaunchedEffect
        delay(350)
        if (cameraPositionState.position.zoom < MIN_CHARGER_ZOOM) {
            onViewportChanged(null)
            return@LaunchedEffect
        }
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        onViewportChanged(
            BoundingBox(
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
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), HOME_ZOOM))
        }
    }

    val routeLatLngs = remember(route) { route?.points.orEmpty().map { it.toLatLng() } }

    // Fit once per new route; afterwards the driver may pan freely.
    LaunchedEffect(mapLoaded, routeLatLngs) {
        if (!mapLoaded || routeLatLngs.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.builder().apply { routeLatLngs.forEach { include(it) } }.build()
        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING_PX))
    }

    val pillIcons = rememberPillIcons()
    // Only flips at the threshold, so panning doesn't recompose every marker.
    val compactMarkers by remember(cameraPositionState) {
        derivedStateOf { cameraPositionState.position.zoom < PILL_ZOOM }
    }
    // Bitmaps can't morph: fade the old tier out, swap at zero, fade the new one in.
    // Only markers on screen at the flip take part; the rest swap silently, so a
    // large loaded set doesn't cost a recomposition per marker per frame.
    var shownCompact by remember { mutableStateOf(compactMarkers) }
    val tierAlpha = remember { Animatable(1f) }
    var fadeBounds by remember { mutableStateOf<LatLngBounds?>(null) }
    LaunchedEffect(compactMarkers) {
        if (shownCompact == compactMarkers) return@LaunchedEffect
        fadeBounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
        tierAlpha.animateTo(0f, tween(TIER_FADE_OUT_MILLIS))
        shownCompact = compactMarkers
        tierAlpha.animateTo(1f, tween(TIER_FADE_IN_MILLIS))
        fadeBounds = null
    }

    GoogleMap(
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        onMapLoaded = { mapLoaded = true },
        // The bar on top and the trip sheet below cover the map; a route must fit between them.
        contentPadding = PaddingValues(top = TOP_CHROME_HEIGHT, bottom = bottomInset),
        // The SDK's own buttons would sit inside the status bar; ours replace them.
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = false),
        modifier = modifier,
    ) {
        // Keyed by site: the list is re-sorted around the centre on every pan.
        chargers.forEach { charger ->
            key(charger.site.id) {
                val bounds = fadeBounds
                ChargerMarker(
                    charger = charger,
                    icons = pillIcons,
                    compact = shownCompact,
                    // Reading the animation subscribes to it; off-screen markers don't.
                    alpha = if (bounds != null && bounds.contains(charger.site.position.toLatLng())) tierAlpha.value else 1f,
                    onClick = onChargerTapped,
                )
            }
        }
        if (route != null) {
            if (routeLatLngs.size >= 2) {
                Polyline(points = routeLatLngs, color = ROUTE_COLOR, width = 14f)
            }
            route.stops.forEach { stop ->
                key(stop.index) {
                    MarkerComposable(
                        keys = arrayOf<Any>(stop.index, stop.operator.orEmpty()),
                        state = rememberMarkerState(position = stop.position.toLatLng()),
                        anchor = Offset(0.5f, 0.5f),
                        onClick = { onStopTapped(stop.index); true },
                    ) {
                        ChargeBadge(color = operatorColor(stop.operator)) {
                            Text(text = "${stop.index}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Marker(state = rememberMarkerState(position = route.destination.toLatLng()))
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

/** Frankfurt, shown before the first fix. */
private val FALLBACK_CENTER = LatLon(50.11, 8.68)
const val HOME_ZOOM = 11f
private val ROUTE_COLOR = Color(0xFF1A73E8)

/** Below this, no chargers load. */
const val MIN_CHARGER_ZOOM = 10f

/** Below this the markers are dots; pills would pile up. */
const val PILL_ZOOM = 11f
private const val TIER_FADE_OUT_MILLIS = 140
private const val TIER_FADE_IN_MILLIS = 220
private const val BOUNDS_PADDING_PX = 120

/** Status bar, search bar and one row of controls. */
private val TOP_CHROME_HEIGHT = 150.dp

