package de.autoapp.android.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import de.autoapp.android.phone.components.GoButton
import de.autoapp.android.phone.components.NetworkDot
import de.autoapp.android.phone.components.PriceText
import de.autoapp.android.phone.components.RankBadge
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.android.phone.components.sheetListPadding
import de.autoapp.android.phone.theme.ChargeAheadColors
import de.autoapp.android.phone.theme.tabular
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.core.ChargeNowResult
import de.autoapp.shared.core.RelaxedFilter
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Place
import de.autoapp.shared.domain.SavedRoute
import de.autoapp.shared.domain.distanceKmTo
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
    from: LatLon?,
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
        if (chosen != null) { searching = false; return@LaunchedEffect }
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
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = sheetListPadding(),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            val shownResults = results.orEmpty()
            if (chosen == null && shownResults.isNotEmpty()) {
                item {
                    AppCard {
                        shownResults.forEachIndexed { index, place ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            PlaceRow(
                                title = place.name,
                                detail = place.detailLine(),
                                distanceKm = from?.distanceKmTo(place.position),
                                onClick = {
                                    chosen = Destination(place.name, place.position)
                                    query = place.name
                                    results = emptyList()
                                },
                            )
                        }
                    }
                }
            }
            if (query.trim().length < 3 && recent.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.plan_recent), modifier = Modifier.padding(top = 8.dp)) }
                item {
                    AppCard {
                        recent.forEachIndexed { index, destination ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            PlaceRow(
                                title = destination.name,
                                detail = null,
                                distanceKm = from?.distanceKmTo(destination.position),
                                onClick = { chosen = destination; query = destination.name },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The structured address when it says more than the name again — otherwise
 * the description chain, which is what tells two same-name towns apart.
 */
private fun Place.detailLine(): String? =
    address?.takeIf { it.street != null || it.postalCode != null }?.let(ChargeStopFormatter::addressLine)
        ?: description.removePrefix("$name, ").takeIf { it != name }

/** A destination row: name, where it is, how far away — the standard list look. */
@Composable
private fun PlaceRow(title: String, detail: String?, distanceKm: Double?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        distanceKm?.let {
            Text(
                stringResource(R.string.plan_result_distance, it.asKmLabel()),
                style = MaterialTheme.typography.bodySmall.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Below 10 km the decimal matters; above it, it's noise. */
private fun Double.asKmLabel(): String =
    if (this >= 10) roundToInt().toString() else oneDecimal()

/** The best chargers nearby. States: loading, empty, list — plus the relax notice. */
@Composable
fun ChargeNowSheetContent(
    result: ChargeNowResult?,
    loading: Boolean,
    onNavigate: (de.autoapp.shared.core.ChargeNowCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cn_title), style = MaterialTheme.typography.titleMedium)

        when {
            loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 24.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.cn_loading))
                }
            }

            result == null || result.candidates.isEmpty() -> Text(
                stringResource(R.string.cn_empty),
                modifier = Modifier.navigationBarsPadding().padding(bottom = 24.dp),
            )

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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = sheetListPadding(),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    itemsIndexed(result.candidates, key = { _, c -> c.site.id }) { index, candidate ->
                        ChargerRow(rank = index + 1, ranked = true, candidate = candidate, onNavigate = onNavigate)
                    }
                    if (result.more.isNotEmpty()) {
                        item { SectionLabel(stringResource(R.string.cn_more), modifier = Modifier.padding(top = 8.dp)) }
                        itemsIndexed(result.more, key = { _, c -> "more-${c.site.id}" }) { index, candidate ->
                            ChargerRow(rank = result.candidates.size + index + 1, ranked = false, candidate = candidate, onNavigate = onNavigate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargerRow(
    rank: Int,
    ranked: Boolean,
    candidate: de.autoapp.shared.core.ChargeNowCandidate,
    onNavigate: (de.autoapp.shared.core.ChargeNowCandidate) -> Unit,
) {
    AppCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
        ) {
            RankBadge(
                number = rank,
                color = if (ranked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                textColor = if (ranked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    NetworkDot(operatorColor(candidate.site.operator), size = 8.dp)
                    // The network decides where the tap goes — the site name is
                    // usually just the town again, the address line covers it.
                    Text(
                        candidate.site.operator ?: candidate.site.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    stringResource(R.string.cn_distance_power, candidate.distanceKm.oneDecimal(), candidate.maxPowerKw.roundToInt()),
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                ChargeStopFormatter.addressLine(candidate.site)?.let { address ->
                    Text(
                        address,
                        style = MaterialTheme.typography.bodySmall.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Two lines, not one: town and postal code are the
                        // point of this line, and they sit at the end.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            candidate.quote.best?.let { PriceText(it.euroPerKwh) }
            GoButton(
                icon = painterResource(R.drawable.ic_destination),
                contentDescription = stringResource(R.string.cn_navigate, candidate.site.name),
                onClick = { onNavigate(candidate) },
            )
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

    Column(modifier = modifier) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = sheetListPadding(),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            item { Text(stringResource(R.string.routes_title), style = MaterialTheme.typography.titleMedium) }
            item { SectionLabel(stringResource(R.string.routes_saved)) }
            if (saved.isEmpty()) {
                item {
                    AppCard {
                        Text(
                            stringResource(R.string.routes_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                        )
                    }
                }
            }
            items(saved, key = { it.id }) { route ->
                AppCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        Column(Modifier.weight(1f).clickable { onOpen(route.destination) }) {
                            Text(route.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            route.summary?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        GoButton(painterResource(R.drawable.ic_pen), stringResource(R.string.routes_rename), onClick = { renaming = route })
                        GoButton(
                            painterResource(R.drawable.ic_remove),
                            stringResource(R.string.routes_delete),
                            onClick = { onDelete(route) },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.routes_recent), modifier = Modifier.padding(top = 8.dp)) }
            items(recent, key = { "recent-${it.name}-${it.position.lat}" }) { destination ->
                val alreadySaved = saved.any { it.destination.position == destination.position }
                AppCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        Text(
                            destination.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable { onOpen(destination) },
                        )
                        GoButton(
                            icon = painterResource(if (alreadySaved) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                            contentDescription = stringResource(R.string.routes_save_recent),
                            onClick = { if (!alreadySaved) onFavorite(destination) },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
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
