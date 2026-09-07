package de.autoapp.android.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import de.autoapp.android.BuildConfig
import de.autoapp.android.R
import de.autoapp.shared.domain.ChargeStop
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Reachability

/**
 * The real map (decision 2026-09-07: Google Maps Compose — the key was
 * available and the hand-off targets Google Maps anyway). Everything the
 * placeholder showed, now on tiles: charging stops as markers, the planned
 * route as a line, the own position via the Maps location layer.
 *
 * Without a `googleMapsApiKey` in `local.properties` the Maps SDK renders a
 * blank grey nothing — worse than honest. The callers therefore fall back to
 * [MapCanvas] via [hasGoogleMapsKey], same philosophy as the demo data.
 */
val hasGoogleMapsKey: Boolean get() = BuildConfig.HAS_GOOGLE_MAPS_KEY

private fun LatLon.toLatLng() = LatLng(lat, lon)

/** Home map: live corridor stops around the own position. */
@Composable
fun HomeGoogleMap(
    position: LatLon?,
    stops: List<ChargeStop>,
    hasLocationPermission: Boolean,
    onStopTapped: (ChargeStop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(
            (position ?: FALLBACK_CENTER).toLatLng(),
            HOME_ZOOM,
        )
    }

    // Follow the first fix, then leave the camera to the user — a map that
    // keeps snapping back is unusable for looking around.
    var followedFirstFix by remember { mutableStateOf(false) }
    LaunchedEffect(position != null) {
        val target = position ?: return@LaunchedEffect
        if (!followedFirstFix) {
            followedFirstFix = true
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(target.toLatLng(), HOME_ZOOM),
            )
        }
    }

    GoogleMap(
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
        modifier = modifier,
    ) {
        stops.forEach { stop ->
            key(stop.site.id) {
                MarkerComposable(
                    keys = arrayOf<Any>(stop.site.id, stop.reachability),
                    state = rememberMarkerState(position = stop.site.position.toLatLng()),
                    title = stop.site.name,
                    onClick = { onStopTapped(stop); true },
                ) {
                    ChargePin(color = stop.reachability.pinColor())
                }
            }
        }
    }
}

/**
 * The same pin-with-bolt the car list uses, in the same reachability colors —
 * the two surfaces must not tell different stories about the same site
 * (AGENTS.md). White under-layer for contrast on any map ground.
 */
@Composable
private fun ChargePin(color: Color) {
    Box(contentAlignment = Alignment.BottomCenter) {
        Icon(
            painter = painterResource(R.drawable.ic_charge_pin),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(44.dp),
        )
        Icon(
            painter = painterResource(R.drawable.ic_charge_pin),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(36.dp),
        )
    }
}

/** Reachability → the colors the car UI already uses; unknown stays neutral, not falsely green. */
private fun Reachability.pinColor(): Color = when (this) {
    Reachability.REACHABLE -> Color(0xFF188038)
    Reachability.MARGINAL -> Color(0xFFF9AB00)
    Reachability.UNREACHABLE -> Color(0xFFD93025)
    Reachability.UNKNOWN -> Color(0xFF1A73E8)
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

    LaunchedEffect(routePoints) {
        if (routePoints.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.builder()
            .apply { routePoints.forEach { include(it.toLatLng()) } }
            .build()
        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING_PX))
    }

    GoogleMap(
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
        modifier = modifier,
    ) {
        if (routePoints.size >= 2) {
            Polyline(
                points = routePoints.map { it.toLatLng() },
                color = androidx.compose.ui.graphics.Color(0xFF1A73E8),
                width = 14f,
            )
        }
        stops.forEach { (index, position) ->
            key(index) {
                MarkerComposable(
                    keys = arrayOf(index),
                    state = rememberMarkerState(position = position.toLatLng()),
                ) {
                    NumberedStopPin(index)
                }
            }
        }
        Marker(state = rememberMarkerState(position = destination.toLatLng()))
    }
}

/** Frankfurt — dead center of the target market, only shown before the first fix. */
private val FALLBACK_CENTER = LatLon(50.11, 8.68)
private const val HOME_ZOOM = 11f
private const val BOUNDS_PADDING_PX = 120

/** Planned stop: its number in a route-colored disc — matches the list numbering. */
@Composable
private fun NumberedStopPin(index: Int) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_charge_pin),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(46.dp),
        )
        Icon(
            painter = painterResource(R.drawable.ic_charge_pin),
            contentDescription = null,
            tint = Color(0xFF1A73E8),
            modifier = Modifier.size(38.dp),
        )
        Text(
            text = "$index",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }
}
