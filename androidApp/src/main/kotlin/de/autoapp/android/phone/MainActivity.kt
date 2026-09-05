package de.autoapp.android.phone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.autoapp.android.ChargeStopsFeatureProvider
import de.autoapp.android.R
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.ChargeStopsState
import de.autoapp.shared.domain.ChargeStop
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.Place
import de.autoapp.shared.domain.SoCSourceKind
import de.autoapp.shared.platformName
import kotlinx.coroutines.launch

/**
 * Phone UI for verification. Actual operation happens in Android Auto (see
 * ChargeStopsScreen); this mainly lets you check that location, API key, and
 * data source work together, without needing a head unit.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChargeStopsPhoneScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChargeStopsPhoneScreen() {
    val context = LocalContext.current
    val settings = remember { ChargeStopsFeatureProvider.settingsStore(context) }
    val feature = remember { ChargeStopsFeatureProvider.create(context) }
    val state by feature.state.collectAsState()
    val vehicle by settings.vehicle.collectAsState(initial = null)
    val manualSoc by settings.manualSocPercent.collectAsState(initial = null)
    val networks by settings.networks.collectAsState(initial = de.autoapp.shared.domain.NetworkPreferences())
    val diagnostics by settings.socDiagnostics.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val destination by settings.destination.collectAsState(initial = null)
    val recentDestinations by settings.recentDestinations.collectAsState(initial = emptyList())

    var openPage by remember { mutableStateOf(Page.STOPS) }
    var detailStop by remember { mutableStateOf<ChargeStop?>(null) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (openPage) {
                                Page.STOPS -> R.string.phone_title_stops
                                Page.VEHICLE -> R.string.phone_settings_title
                                Page.DESTINATION -> R.string.phone_destination_title
                                Page.NETWORKS -> R.string.phone_networks_title
                            },
                        ),
                    )
                },
                actions = {
                    if (openPage != Page.STOPS) {
                        TextButton(onClick = { openPage = Page.STOPS }) {
                            Text(stringResource(R.string.phone_action_close))
                        }
                    } else {
                        TextButton(onClick = { openPage = Page.DESTINATION }) {
                            Text(stringResource(R.string.phone_action_destination))
                        }
                        TextButton(onClick = { openPage = Page.NETWORKS }) {
                            Text(stringResource(R.string.phone_action_networks))
                        }
                        TextButton(onClick = { openPage = Page.VEHICLE }) {
                            Text(stringResource(R.string.phone_action_settings))
                        }
                        // As an icon rather than a word: with four text
                        // buttons there's no room left for the title, and
                        // the header wraps it mid-word.
                        IconButton(onClick = { feature.refresh() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = stringResource(R.string.phone_action_refresh),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (openPage) {
            Page.VEHICLE -> {
                VehicleSettingsScreen(
                    vehicle = vehicle,
                    socPercent = manualSoc,
                    socFromCar = state.socSource == SoCSourceKind.CAR_HARDWARE,
                    onVehicleChange = { scope.launch { settings.setVehicle(it) } },
                    onSocChange = { scope.launch { settings.setManualSocPercent(it) } },
                    diagnostics = diagnostics,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                return@Scaffold
            }

            Page.DESTINATION -> {
                DestinationScreen(
                    current = destination,
                    recent = recentDestinations,
                    // null means "unreachable" and is different from an
                    // empty results list.
                    onSearch = { query ->
                        runCatching { feature.searchDestinations(query) }.getOrNull()
                    },
                    onSelect = { chosen ->
                        scope.launch { feature.setDestination(chosen) }
                        openPage = Page.STOPS
                    },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                return@Scaffold
            }

            Page.NETWORKS -> {
                NetworkSettingsScreen(
                    preferences = networks,
                    available = state.availableOperators,
                    onChange = { scope.launch { settings.setNetworks(it) } },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                return@Scaffold
            }

            Page.STOPS -> Unit
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.phone_platform_label) + ": " + platformName(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.phone_hint_car_ui),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (state.isDemo) {
                // Presenting made-up charging sites as real ones would, in
                // an app for the car, not just be sloppy but dangerous.
                Text(
                    text = stringResource(R.string.phone_demo_notice),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            routeText(state)?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (state.networkFilterActive) {
                Text(
                    text = stringResource(R.string.phone_networks_filtered),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (vehicle == null || manualSoc == null) {
                // Without both, reachability stays UNKNOWN. That's not an
                // error, but the driver should know why no colors or
                // arrival percentages appear.
                Text(
                    text = stringResource(R.string.phone_settings_missing),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            HorizontalDivider()

            if (!hasPermission) {
                MissingPermission(
                    onRequest = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                )
                return@Column
            }

            statusText(state)?.let { status ->
                Text(text = status, modifier = Modifier.padding(16.dp))
            }

            Text(
                text = stringResource(R.string.phone_stops_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            detailStop?.let { stop ->
                ChargeStopDetailDialog(stop = stop, onDismiss = { detailStop = null })
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.stops, key = { it.site.id }) { stop ->
                    ChargeStopRow(stop, onClick = { detailStop = stop })
                }
                // Attribution requirement: OpenStreetMap is licensed under ODbL.
                item {
                    Text(
                        text = stringResource(R.string.phone_attribution),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingPermission(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.phone_permission_message))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.phone_permission_action))
        }
    }
}

/** A word about the situation — or nothing, when the list speaks for itself. */
@Composable
private fun statusText(state: ChargeStopsState): String? = when (state.phase) {
    ChargeStopsState.Phase.WAITING_FOR_LOCATION -> stringResource(R.string.phone_status_waiting)

    ChargeStopsState.Phase.LOADING ->
        if (state.stops.isEmpty()) stringResource(R.string.phone_status_loading) else null

    ChargeStopsState.Phase.READY ->
        if (state.stops.isEmpty()) stringResource(R.string.phone_status_no_stops) else null

    ChargeStopsState.Phase.FAILED -> when (state.failure) {
        ChargeStopsState.FailureReason.LOCATION_UNAVAILABLE ->
            stringResource(R.string.phone_status_location_unavailable)

        ChargeStopsState.FailureReason.SITES_UNAVAILABLE, null ->
            if (state.stops.isEmpty()) {
                stringResource(R.string.phone_status_sites_unavailable)
            } else {
                stringResource(R.string.phone_status_stale)
            }
    }
}

/** Which page is currently open. Sufficient without a navigation library. */
private enum class Page { STOPS, VEHICLE, DESTINATION, NETWORKS }

/** A sentence about whether the search is along the route or by heading. */
@Composable
private fun routeText(state: ChargeStopsState): String? {
    val name = state.destination?.name ?: return null
    return when (state.routeStatus) {
        ChargeStopsState.RouteStatus.ACTIVE -> stringResource(R.string.phone_route_active, name)
        ChargeStopsState.RouteStatus.CALCULATING -> stringResource(R.string.phone_route_calculating)
        ChargeStopsState.RouteStatus.UNAVAILABLE ->
            stringResource(R.string.phone_route_unavailable, name)

        ChargeStopsState.RouteStatus.NONE -> null
    }
}

@Composable
private fun ChargeStopRow(stop: ChargeStop, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = listOfNotNull(stop.site.name, stop.site.operator).joinToString(" · "),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(text = ChargeStopFormatter.primaryLine(stop))
        Text(
            text = ChargeStopFormatter.secondaryLine(stop),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HorizontalDivider()
}

private fun android.content.Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * The same information as in the car, so the two UIs don't drift apart —
 * just as a dialog instead of its own screen.
 */
@Composable
private fun ChargeStopDetailDialog(stop: ChargeStop, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stop.site.name) },
        text = {
            Column {
                Text(ChargeStopFormatter.primaryLine(stop))
                Text(
                    text = ChargeStopFormatter.secondaryLine(stop),
                    style = MaterialTheme.typography.bodySmall,
                )

                stop.site.operator?.let {
                    Text(text = it, modifier = Modifier.padding(top = 8.dp))
                }
                ChargeStopFormatter.addressLine(stop)?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }

                Text(
                    text = stringResource(R.string.phone_detail_connectors),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                val connectorLines = ChargeStopFormatter.connectorLines(stop)
                if (connectorLines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.phone_detail_unknown_connectors),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    connectorLines.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall) }
                }

                ChargeStopFormatter.sourceLine(stop)?.let { source ->
                    Text(
                        text = stringResource(R.string.phone_detail_source) + ": " + source,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            M3TextButton(onClick = { context.startNavigationTo(stop); onDismiss() }) {
                Text(stringResource(R.string.phone_detail_navigate))
            }
        },
        dismissButton = {
            M3TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.phone_action_close))
            }
        },
    )
}

/**
 * Coordinates **and** name: the coordinates lead exactly there, the name
 * appears to the driver as the destination.
 */
private fun android.content.Context.startNavigationTo(stop: ChargeStop) {
    val position = stop.site.position
    val label = Uri.encode(stop.site.name)
    val uri = Uri.parse("geo:${position.lat},${position.lon}?q=${position.lat},${position.lon}($label)")

    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (notFound: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.phone_detail_no_navigation), Toast.LENGTH_LONG).show()
    }
}
