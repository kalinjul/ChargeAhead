package de.autoapp.android.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.SearchField
import de.autoapp.android.phone.components.TickRow
import de.autoapp.shared.ui.NetworksUiState
import de.autoapp.shared.ui.NetworksViewModel

/**
 * Selecting charging networks.
 *
 * The list comes from the charging sites in the current surroundings, not
 * from a maintained enumeration. That has two advantages: it's never
 * incomplete, and it only shows what actually occurs here — a selection of
 * a hundred providers, two of which are nearby, would help no one.
 *
 * Still, it quickly grows to dozens of entries, hence the search. "All" and
 * "None" act on the **displayed** list: with "ionity" in the search field,
 * one tap selects every spelling variant of that network without touching
 * the rest of the selection. That's the reason the search is more than just
 * a reading aid.
 *
 * Without a search term the driver's own networks come first: they are the
 * ones you return to in order to change something, and they'd otherwise sit
 * scattered through a list of hundreds.
 */
@Composable
fun NetworksRoute(
    modifier: Modifier = Modifier,
    viewModel: NetworksViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NetworkSettingsScreen(
        uiState = uiState,
        onSearchChange = viewModel::onSearchChanged,
        onOnlyPreferredChange = viewModel::onOnlyPreferredChanged,
        onOperatorToggle = viewModel::onOperatorToggled,
        onSelectAllShown = viewModel::onAllShownSelected,
        onSelectNoneShown = viewModel::onNoShownSelected,
        modifier = modifier,
    )
}

@Composable
fun NetworkSettingsScreen(
    uiState: NetworksUiState,
    onSearchChange: (String) -> Unit,
    onOnlyPreferredChange: (Boolean) -> Unit,
    onOperatorToggle: (String) -> Unit,
    onSelectAllShown: () -> Unit,
    onSelectNoneShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 18.dp)) {
        Fineprint(
            text = stringResource(R.string.phone_networks_intro),
            modifier = Modifier.padding(top = 16.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Switch(
                checked = uiState.preferences.onlyPreferred,
                onCheckedChange = onOnlyPreferredChange,
            )
            Text(
                text = stringResource(R.string.phone_networks_only),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        if (uiState.available.isEmpty()) {
            if (uiState.loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Fineprint(
                        text = stringResource(R.string.phone_networks_loading),
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            } else {
                Fineprint(
                    text = stringResource(R.string.phone_networks_empty),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            return@Column
        }

        SearchField(
            value = uiState.search,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.phone_networks_search),
            modifier = Modifier.padding(top = 16.dp),
        )

        val options = uiState.shown
        if (options == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Fineprint(
                    text = stringResource(R.string.phone_networks_filtering),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            return@Column
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            OutlinedButton(
                enabled = options.isNotEmpty(),
                shape = MaterialTheme.shapes.small,
                onClick = onSelectAllShown,
            ) {
                Text(stringResource(R.string.phone_networks_all))
            }
            OutlinedButton(
                enabled = options.isNotEmpty(),
                shape = MaterialTheme.shapes.small,
                onClick = onSelectNoneShown,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.phone_networks_none))
            }
        }

        // The hint only appears while searching: without search text,
        // "displayed" equals "all", where it would just be noise.
        if (uiState.query.isNotBlank()) {
            Fineprint(
                text = stringResource(R.string.phone_networks_bulk_hint),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (options.isEmpty()) {
            Fineprint(
                text = stringResource(R.string.phone_networks_no_match, uiState.query.trim()),
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }

        // The rows are laid out lazily, not all at once inside a single item:
        // in a well-covered area the list runs to a few hundred networks, and
        // composing all of them is what made opening this screen slow.
        //
        // Deliberately no key from the data: operator names come from the
        // sources, and two identical keys crash the app mid-composition.
        AppCard(modifier = Modifier.padding(top = 8.dp)) {
            LazyColumn {
                itemsIndexed(options) { index, option ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    val checked = option.key in uiState.preferences.preferredOperators
                    TickRow(
                        label = option.displayName,
                        sublabel = pluralStringResource(R.plurals.phone_networks_count, option.siteCount, option.siteCount),
                        checked = checked,
                        dotColor = operatorColor(option.displayName),
                        onClick = { onOperatorToggle(option.key) },
                    )
                }
            }
        }
    }
}
