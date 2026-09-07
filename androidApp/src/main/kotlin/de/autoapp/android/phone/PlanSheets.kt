package de.autoapp.android.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.shared.core.ChargeNowResult
import de.autoapp.shared.core.RelaxedFilter
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.Place
import de.autoapp.shared.domain.SavedRoute
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Destination entry for planning: search via the shared geocoder, recents
 * below — the same data the car uses, presented for thumbs instead of a
 * rotary controller.
 */
@Composable
fun PlanSheetContent(
    recent: List<Destination>,
    vehicleName: String?,
    initialSocPercent: Double?,
    onSearch: suspend (String) -> List<Place>?,
    onPlan: (Destination, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // The one number the plan stands or falls with — so it's set right here,
    // not hidden in the garage. Prefilled from the stored value.
    var soc by remember { mutableStateOf((initialSocPercent ?: 80.0).toFloat()) }

    // Debounced: Nominatim allows one request per second (ROADMAP open item 6),
    // and a request per keystroke would blow through that within a word.
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 3) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        delay(600)
        results = onSearch(trimmed)
        searching = false
    }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.titleLarge)

        if (vehicleName == null) {
            Text(
                stringResource(R.string.plan_vehicle_missing),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                stringResource(R.string.plan_soc_label, vehicleName, soc.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            androidx.compose.material3.Slider(
                value = soc,
                onValueChange = { soc = it },
                valueRange = 1f..100f,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.plan_search_hint)) },
            singleLine = true,
            trailingIcon = { if (searching) CircularProgressIndicator(modifier = Modifier.padding(8.dp)) },
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            results == null -> Text(
                stringResource(R.string.plan_search_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )

            results!!.isEmpty() && query.trim().length >= 3 && !searching -> Text(
                stringResource(R.string.plan_no_results),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results.orEmpty(), key = { it.name + it.position.lat }) { place ->
                Text(
                    text = place.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlan(Destination(place.name, place.position), soc.toDouble()) }
                        .padding(vertical = 10.dp),
                )
                HorizontalDivider()
            }
            if (query.trim().length < 3 && recent.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.plan_recent),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(recent, key = { "recent-${it.name}-${it.position.lat}" }) { destination ->
                    Text(
                        text = destination.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlan(destination, soc.toDouble()) }
                            .padding(vertical = 10.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** The best chargers nearby. States: loading, empty, list — plus the relax notice. */
@Composable
fun ChargeNowSheetContent(
    result: ChargeNowResult?,
    loading: Boolean,
    onNavigate: (de.autoapp.shared.core.ChargeNowCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cn_title), style = MaterialTheme.typography.titleLarge)

        when {
            loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.cn_loading))
                }
            }

            result == null || result.candidates.isEmpty() -> Text(stringResource(R.string.cn_empty))

            else -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                Text(
                    if (result.relaxed.isEmpty()) {
                        stringResource(R.string.cn_subtitle)
                    } else {
                        stringResource(
                            R.string.cn_relaxed,
                            result.relaxed.joinToString { context.getString(it.labelRes()) },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.relaxed.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                result.candidates.forEachIndexed { index, candidate ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${index + 1} · ${candidate.site.name}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(
                                    R.string.cn_distance_power,
                                    candidate.distanceKm.oneDecimal(),
                                    candidate.maxPowerKw.roundToInt(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        candidate.quote.best?.let { best ->
                            Text(
                                stringResource(R.string.cn_price, best.euroPerKwh.twoDecimals()),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        IconButton(onClick = { onNavigate(candidate) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_destination),
                                contentDescription = stringResource(R.string.cn_navigate, candidate.site.name),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun RelaxedFilter.labelRes(): Int = when (this) {
    RelaxedFilter.MIN_POWER -> R.string.cn_relax_min_power
    RelaxedFilter.NETWORKS -> R.string.cn_relax_networks
    RelaxedFilter.MAX_PRICE -> R.string.cn_relax_max_price
    RelaxedFilter.MAX_DISTANCE -> R.string.cn_relax_max_distance
}

/** Saved routes on top, recent destinations below with a quick-favorite heart. */
@Composable
fun RoutesSheetContent(
    saved: List<SavedRoute>,
    recent: List<Destination>,
    onOpen: (Destination) -> Unit,
    onRename: (SavedRoute, String) -> Unit,
    onDelete: (SavedRoute) -> Unit,
    onFavorite: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<SavedRoute?>(null) }

    renaming?.let { route ->
        RenameDialog(
            route = route,
            onConfirm = { name -> onRename(route, name); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text(stringResource(R.string.routes_title), style = MaterialTheme.typography.titleLarge) }
        item {
            Text(
                stringResource(R.string.routes_saved),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (saved.isEmpty()) {
            item { Text(stringResource(R.string.routes_empty), style = MaterialTheme.typography.bodySmall) }
        }
        items(saved, key = { it.id }) { route ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpen(route.destination) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(route.name, style = MaterialTheme.typography.titleSmall)
                    route.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                TextButton(onClick = { renaming = route }) { Text(stringResource(R.string.routes_rename)) }
                IconButton(onClick = { onDelete(route) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_remove),
                        contentDescription = stringResource(R.string.routes_delete),
                    )
                }
            }
            HorizontalDivider()
        }
        item {
            Text(
                stringResource(R.string.routes_recent),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        items(recent, key = { "recent-${it.name}-${it.position.lat}" }) { destination ->
            val alreadySaved = saved.any { it.destination.position == destination.position }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    destination.name,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpen(destination) }
                        .padding(vertical = 8.dp),
                )
                IconButton(onClick = { if (!alreadySaved) onFavorite(destination) }) {
                    Icon(
                        painter = painterResource(
                            if (alreadySaved) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                        ),
                        contentDescription = stringResource(R.string.routes_save_recent),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun RenameDialog(route: SavedRoute, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(route.name) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routes_rename_title)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim().ifEmpty { route.name }) }) {
                Text(stringResource(R.string.routes_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.routes_cancel)) }
        },
    )
}
