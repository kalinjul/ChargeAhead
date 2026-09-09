package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.SearchField
import de.autoapp.android.phone.components.TickRow
import de.autoapp.shared.ui.SubscriptionsUiState
import de.autoapp.shared.ui.SubscriptionsViewModel

/**
 * Which tariffs the driver holds. The active set feeds every price
 * comparison — planning, "charge now", and the stop detail all quote against
 * exactly these plus ad-hoc.
 */
@Composable
fun SubscriptionsRoute(
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SubscriptionsScreen(
        uiState = uiState,
        onSearchChange = viewModel::onQueryChanged,
        onToggle = viewModel::onTariffToggled,
        modifier = modifier,
    )
}

@Composable
fun SubscriptionsScreen(
    uiState: SubscriptionsUiState,
    onSearchChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hits = uiState.matches

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SearchField(
            value = uiState.query,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.subs_search),
            modifier = Modifier.padding(top = 14.dp),
        )
        Fineprint(stringResource(R.string.subs_note))
        if (hits.isEmpty()) {
            Text(
                stringResource(R.string.subs_none_found),
            )
        }
        LazyColumn {
            item {
                AppCard {
                    hits.forEachIndexed { index, tariff ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        val checked = tariff.id in uiState.activeIds
                        TickRow(
                            label = tariff.displayName,
                            sublabel = tariff.monthlyFeeEuro
                                ?.let { stringResource(R.string.subs_fee, it.twoDecimals()) }
                                ?: stringResource(R.string.subs_no_fee),
                            checked = checked,
                            onClick = { onToggle(tariff.id) },
                        )
                    }
                }
            }
        }
    }
}
