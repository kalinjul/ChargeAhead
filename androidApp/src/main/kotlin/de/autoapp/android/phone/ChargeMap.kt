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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import de.autoapp.android.BuildConfig
import de.autoapp.android.R
import de.autoapp.shared.domain.ChargeSpeed
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.OperatorShortName

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

/**
 * Home map: viewport-driven. The map reports every settled camera position
 * upward (`null` below [MIN_CHARGER_ZOOM]); the caller loads the chargers for
 * it and passes them back down. No coupling to the car's corridor feature —
 * the map shows what the camera looks at, not what lies in driving direction.
 */
@Composable
fun HomeGoogleMap(
    position: LatLon?,
    chargers: List<de.autoapp.shared.MapCharger>,
    hasLocationPermission: Boolean,
    onViewportChanged: (de.autoapp.shared.domain.BoundingBox?) -> Unit,
    onChargerTapped: (de.autoapp.shared.MapCharger) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(
            (position ?: FALLBACK_CENTER).toLatLng(),
            HOME_ZOOM,
        )
    }

    // Debounced camera-idle: load once the camera settles, not per frame of
    // a fling. Also fires for the initial position.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) return@LaunchedEffect
        kotlinx.coroutines.delay(350)
        if (cameraPositionState.position.zoom < MIN_CHARGER_ZOOM) {
            onViewportChanged(null)
            return@LaunchedEffect
        }
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        onViewportChanged(
            de.autoapp.shared.domain.BoundingBox(
                south = bounds.southwest.latitude,
                west = bounds.southwest.longitude,
                north = bounds.northeast.latitude,
                east = bounds.northeast.longitude,
            ),
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

    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        GoogleMap(
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            // The SDK's own buttons render at the map's top edge — on a
            // fullscreen map that's inside the status bar, unreachable. Our
            // replacements sit below, top right.
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            chargers.forEach { charger ->
                val label = OperatorShortName.of(charger.site.operator)
                val speed = ChargeSpeed.of(charger.maxPowerKw)
                key(charger.site.id) {
                    MarkerComposable(
                        keys = arrayOf<Any>(charger.site.id, speed, label.orEmpty()),
                        state = rememberMarkerState(position = charger.site.position.toLatLng()),
                        title = charger.site.name,
                        anchor = Offset(0.5f, 0.5f),
                        onClick = { onChargerTapped(charger); true },
                    ) {
                        ChargerPill(speed = speed, label = label)
                    }
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
                onClick = {
                    position?.let {
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(it.toLatLng(), HOME_ZOOM),
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = stringResource(R.string.map_my_location),
                    tint = MaterialTheme.colorScheme.primary,
                )
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
                    // Counter-rotated like a real compass needle: it points
                    // north however the map is turned.
                    modifier = Modifier.rotate(-cameraPositionState.position.bearing),
                )
            }
        }
    }
}

/**
 * Small white chip, like the hotel/POI price chips in Google Maps: one bolt
 * per power class, colored by [ChargeSpeed].
 *
 * The color carries charging speed, not reachability — color-coding
 * reachability on a map read as arbitrary noise (design interview
 * 2026-09-07), while speed is the one property the driver compares between
 * two pins at map scale. The bolt count repeats that ordering without color,
 * for anyone who cannot tell the red from the green one.
 *
 * The label names the network, nothing else. The price used to sit here and
 * doesn't any more: every price this app knows is an estimate against the
 * driver's tariffs, and a number that precise on a pin reads as a fact. It
 * belongs where the caveat fits with it — one tap away, in the detail view.
 *
 * Sites whose operator isn't one of the well-known networks show their bolts
 * alone; see [OperatorShortName].
 */
@Composable
private fun ChargerPill(speed: ChargeSpeed, label: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-3).dp),
        modifier = Modifier
            .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(50))
            .border(1.dp, Color(0xFFDADCE0), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        repeat(speed.bolts) {
            Icon(
                painter = painterResource(R.drawable.ic_bolt),
                contentDescription = null,
                tint = speed.markerColor(),
                modifier = Modifier.size(14.dp),
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF202124),
                modifier = Modifier.padding(start = 5.dp, end = 2.dp),
            )
        }
    }
}

/** Red below 50 kW, amber up to 100, green above — the traffic light everyone reads without a legend. */
private fun ChargeSpeed.markerColor(): Color = when (this) {
    ChargeSpeed.SLOW -> Color(0xFFD93025)
    ChargeSpeed.MEDIUM -> Color(0xFFF9AB00)
    ChargeSpeed.FAST, ChargeSpeed.ULTRA, ChargeSpeed.HYPER -> Color(0xFF188038)
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

/** Frankfurt — dead center of the target market, only shown before the first fix. */
private val FALLBACK_CENTER = LatLon(50.11, 8.68)
private const val HOME_ZOOM = 11f

/** Below this, no chargers load and none show — the map stays clean and cheap. */
const val MIN_CHARGER_ZOOM = 10f
private const val BOUNDS_PADDING_PX = 120

