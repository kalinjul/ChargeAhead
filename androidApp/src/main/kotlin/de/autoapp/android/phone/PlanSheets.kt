package de.autoapp.android.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.AppChip
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.android.phone.theme.ChargeAheadColors
import de.autoapp.android.phone.theme.tabular
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
    // The one number the plan stands or falls with — so it's typed right
    // here, not hidden in the garage. Prefilled from the stored value.
    var socText by remember { mutableStateOf((initialSocPercent ?: 80.0).roundToInt().toString()) }
    val socPercent = socText.toIntOrNull()?.takeIf { it in 1..100 }
    var chosen by remember { mutableStateOf<Destination?>(null) }

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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.titleMedium)

        if (vehicleName == null) {
            Text(
                stringResource(R.string.plan_vehicle_missing),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // The mockup's routecard: From is fixed, To is the live search field.
        AppCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Column {
                    SectionLabel(stringResource(R.string.plan_from), modifier = Modifier.padding(0.dp))
                    Text(stringResource(R.string.plan_from_current), style = MaterialTheme.typography.bodyLarge)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp),
            ) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp)))
                Column(Modifier.weight(1f)) {
                    SectionLabel(stringResource(R.string.plan_to), modifier = Modifier.padding(0.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; chosen = null },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.plan_search_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ChargeAheadColors.faint,
                                )
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    )
                }
                if (searching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        vehicleName?.let { name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip(text = name, icon = painterResource(R.drawable.ic_car))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        BasicTextField(
                            value = socText,
                            onValueChange = { socText = it.filter(Char::isDigit).take(3) },
                            textStyle = MaterialTheme.typography.labelMedium.tabular.copy(
                                color = if (socPercent == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(28.dp),
                        )
                        Text("%", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Fineprint(stringResource(R.string.plan_soc_hint))
            }
        }

        when {
            results == null -> Text(
                stringResource(R.string.plan_search_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            results!!.isEmpty() && query.trim().length >= 3 && !searching && chosen == null -> Text(
                stringResource(R.string.plan_no_results),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = { chosen?.let { destination -> socPercent?.let { onPlan(destination, it.toDouble()) } } },
            enabled = chosen != null && socPercent != null && vehicleName != null,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(15.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_route), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text(stringResource(R.string.plan_cta), style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
        }

        // Results while typing; recents when idle — the expanded sheet's "fullonly".
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
            if (chosen == null) {
                items(results.orEmpty(), key = { it.name + it.position.lat }) { place ->
                    AppCard(onClick = {
                        chosen = Destination(place.name, place.position)
                        query = place.name
                        results = emptyList()
                    }) {
                        Text(
                            place.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            if (query.trim().length < 3 && recent.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.plan_recent), modifier = Modifier.padding(top = 8.dp)) }
                items(recent, key = { "recent-${it.name}-${it.position.lat}" }) { destination ->
                    AppCard(onClick = { chosen = destination; query = destination.name }) {
                        Text(
                            destination.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
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
