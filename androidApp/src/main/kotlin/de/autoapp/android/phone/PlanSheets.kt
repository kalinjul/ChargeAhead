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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.android.phone.components.StationCard
import de.autoapp.android.phone.components.sheetListPadding
import de.autoapp.android.phone.theme.ChargeAheadColors
import de.autoapp.android.phone.theme.tabular
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.core.RelaxedFilter
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.Place
import de.autoapp.shared.domain.SavedRoute
import de.autoapp.shared.domain.distanceKmTo
import de.autoapp.shared.ui.ChargeNowUiState
import de.autoapp.shared.ui.ChargeNowViewModel
import de.autoapp.shared.ui.PlanSheetUiState
import de.autoapp.shared.ui.PlanSheetViewModel
import de.autoapp.shared.ui.RoutesUiState
import de.autoapp.shared.ui.RoutesViewModel
import kotlin.math.roundToInt

/**
 * Destination entry for planning: search via the shared geocoder, recents
 * below — the same data the car uses, presented for thumbs instead of a
 * rotary controller.
 */
@Composable
fun PlanSheetRoute(
    onPlan: (Destination, Double) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanSheetViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlanSheetContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChanged,
        onDestinationChosen = viewModel::onDestinationChosen,
        onSocChange = viewModel::onSocChanged,
        onPlan = onPlan,
        modifier = modifier,
    )
}

@Composable
fun PlanSheetContent(
    uiState: PlanSheetUiState,
    onQueryChange: (String) -> Unit,
    onDestinationChosen: (Destination) -> Unit,
    onSocChange: (String) -> Unit,
    onPlan: (Destination, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicleName = uiState.vehicleName
    val socPercent = uiState.socPercent

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
                        value = uiState.query,
                        onValueChange = onQueryChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (uiState.query.isEmpty()) {
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
                if (uiState.searching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
                            value = uiState.socInput,
                            onValueChange = onSocChange,
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
            uiState.results == null -> Text(
                stringResource(R.string.plan_search_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            uiState.results.orEmpty().isEmpty() && !uiState.isQueryTooShort &&
                !uiState.searching && uiState.chosen == null -> Text(
                stringResource(R.string.plan_no_results),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = { uiState.chosen?.let { destination -> socPercent?.let { onPlan(destination, it.toDouble()) } } },
            enabled = uiState.canPlan,
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
            val shownResults = uiState.results.orEmpty()
            if (uiState.chosen == null && shownResults.isNotEmpty()) {
                item {
                    AppCard {
                        shownResults.forEachIndexed { index, place ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            PlaceRow(
                                title = place.name,
                                detail = place.detailLine(),
                                distanceKm = uiState.from?.distanceKmTo(place.position),
                                onClick = { onDestinationChosen(Destination(place.name, place.position)) },
                            )
                        }
                    }
                }
            }
            if (uiState.isQueryTooShort && uiState.recent.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.plan_recent), modifier = Modifier.padding(top = 8.dp)) }
                item {
                    AppCard {
                        uiState.recent.forEachIndexed { index, destination ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            PlaceRow(
                                title = destination.name,
                                detail = null,
                                distanceKm = uiState.from?.distanceKmTo(destination.position),
                                onClick = { onDestinationChosen(destination) },
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

@Composable
fun ChargeNowRoute(
    onNavigate: (de.autoapp.shared.core.ChargeNowCandidate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChargeNowViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChargeNowSheetContent(uiState = uiState, onNavigate = onNavigate, modifier = modifier)
}

/** The best chargers nearby. States: no position, loading, empty, list — plus the relax notice. */
@Composable
fun ChargeNowSheetContent(
    uiState: ChargeNowUiState,
    onNavigate: (de.autoapp.shared.core.ChargeNowCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cn_title), style = MaterialTheme.typography.titleMedium)

        when (uiState) {
            ChargeNowUiState.NoPosition -> Text(
                stringResource(R.string.home_no_position),
                modifier = Modifier.navigationBarsPadding().padding(bottom = 24.dp),
            )

            ChargeNowUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 24.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.cn_loading))
                }
            }

            is ChargeNowUiState.Ready -> {
                val result = uiState.result
                if (result.candidates.isEmpty()) {
                    Text(
                        stringResource(R.string.cn_empty),
                        modifier = Modifier.navigationBarsPadding().padding(bottom = 24.dp),
                    )
                    return@Column
                }
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
                        ChargeNowCard(rank = index + 1, candidate = candidate, onNavigate = onNavigate)
                    }
                    if (result.more.isNotEmpty()) {
                        item { SectionLabel(stringResource(R.string.cn_more), modifier = Modifier.padding(top = 8.dp)) }
                        itemsIndexed(result.more, key = { _, c -> "more-${c.site.id}" }) { index, candidate ->
                            ChargeNowCard(
                                rank = result.candidates.size + index + 1,
                                candidate = candidate,
                                onNavigate = onNavigate,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargeNowCard(
    rank: Int,
    candidate: de.autoapp.shared.core.ChargeNowCandidate,
    onNavigate: (de.autoapp.shared.core.ChargeNowCandidate) -> Unit,
) {
    StationCard(
        rank = rank,
        badgeColor = operatorColor(candidate.site.operator),
        // The network decides where the tap goes — the site name is
        // usually just the town again, the address line covers it.
        title = candidate.site.operator ?: candidate.site.name,
        metaLine = stringResource(R.string.cn_distance_power, candidate.distanceKm.oneDecimal(), candidate.maxPowerKw.roundToInt()),
        address = ChargeStopFormatter.addressLine(candidate.site),
        priceEuroPerKwh = candidate.quote.best?.euroPerKwh,
        onSend = { onNavigate(candidate) },
        sendContentDescription = stringResource(R.string.cn_navigate, candidate.site.name),
    )
}

private fun RelaxedFilter.labelRes(): Int = when (this) {
    RelaxedFilter.MIN_POWER -> R.string.cn_relax_min_power
    RelaxedFilter.NETWORKS -> R.string.cn_relax_networks
    RelaxedFilter.MAX_PRICE -> R.string.cn_relax_max_price
    RelaxedFilter.MAX_DISTANCE -> R.string.cn_relax_max_distance
}

@Composable
fun RoutesRoute(
    onOpen: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutesViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RoutesSheetContent(
        uiState = uiState,
        onOpen = onOpen,
        onRename = viewModel::onRenamed,
        onDelete = viewModel::onDeleted,
        onFavorite = viewModel::onFavourited,
        modifier = modifier,
    )
}

/** Saved routes on top, recent destinations below with a quick-favorite heart. */
@Composable
fun RoutesSheetContent(
    uiState: RoutesUiState,
    onOpen: (Destination) -> Unit,
    onRename: (SavedRoute, String) -> Unit,
    onDelete: (SavedRoute) -> Unit,
    onFavorite: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val saved = uiState.saved
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
            items(uiState.recent, key = { "recent-${it.name}-${it.position.lat}" }) { destination ->
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
