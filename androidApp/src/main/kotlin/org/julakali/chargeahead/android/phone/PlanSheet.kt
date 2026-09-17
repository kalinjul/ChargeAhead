package org.julakali.chargeahead.android.phone

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.components.AppChip
import org.julakali.chargeahead.android.phone.components.Fineprint
import org.julakali.chargeahead.android.phone.components.SectionLabel
import org.julakali.chargeahead.android.phone.components.SocEditDialog
import org.julakali.chargeahead.android.phone.components.sheetListPadding
import org.julakali.chargeahead.android.phone.theme.ChargeAheadColors
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.distanceKmTo
import org.julakali.chargeahead.shared.ui.PlanSheetUiState
import org.julakali.chargeahead.shared.ui.PlanSheetViewModel
import kotlin.math.roundToInt

/** Destination entry for planning: search via the shared geocoder, recents below. */
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
        onPlaceChosen = viewModel::onPlaceChosen,
        onSocEdit = viewModel::onSocEditRequested,
        onSocInputChange = viewModel::onSocInputChanged,
        onSocConfirm = viewModel::onSocConfirmed,
        onSocDismiss = viewModel::onSocEditDismissed,
        onPlan = onPlan,
        modifier = modifier,
    )
}

@Composable
fun PlanSheetContent(
    uiState: PlanSheetUiState,
    onQueryChange: (String) -> Unit,
    onDestinationChosen: (Destination) -> Unit,
    onPlaceChosen: (Place) -> Unit,
    onSocEdit: () -> Unit,
    onSocInputChange: (String) -> Unit,
    onSocConfirm: () -> Unit,
    onSocDismiss: () -> Unit,
    onPlan: (Destination, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicleName = uiState.vehicleName
    val socPercent = uiState.socPercent
    // A picked destination clears focus.
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.titleMedium)

        if (vehicleName == null) {
            Text(
                stringResource(R.string.plan_vehicle_missing),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // From is fixed, To is the live search field.
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
                // Shows the level; editing happens in the shared dialog.
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        stringResource(R.string.plan_soc_value, uiState.socInput),
                        style = MaterialTheme.typography.labelMedium.tabular,
                        color = if (socPercent == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable(onClick = onSocEdit)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }

        uiState.socEditorInput?.let { input ->
            SocEditDialog(
                value = input,
                title = stringResource(R.string.soc_dialog_title),
                confirmLabel = stringResource(R.string.soc_dialog_apply),
                onValueChange = onSocInputChange,
                onConfirm = onSocConfirm,
                onDismiss = onSocDismiss,
            )
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

        // Results while typing; recents when idle.
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
                                detail = ChargeStopFormatter.detailLine(place),
                                distanceKm = uiState.from?.distanceKmTo(place.position),
                                onClick = {
                                    focusManager.clearFocus()
                                    onPlaceChosen(place)
                                },
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
                                detail = destination.address,
                                distanceKm = uiState.from?.distanceKmTo(destination.position),
                                onClick = {
                                    focusManager.clearFocus()
                                    onDestinationChosen(destination)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A destination row: name, where it is, how far away. */
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

/** One decimal below 10 km. */
private fun Double.asKmLabel(): String =
    if (this >= 10) roundToInt().toString() else oneDecimal()
