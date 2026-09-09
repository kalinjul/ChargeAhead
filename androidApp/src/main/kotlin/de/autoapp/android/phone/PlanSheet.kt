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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.AppChip
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.android.phone.components.sheetListPadding
import de.autoapp.android.phone.theme.ChargeAheadColors
import de.autoapp.android.phone.theme.tabular
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.Place
import de.autoapp.shared.domain.distanceKmTo
import de.autoapp.shared.ui.PlanSheetUiState
import de.autoapp.shared.ui.PlanSheetViewModel
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
