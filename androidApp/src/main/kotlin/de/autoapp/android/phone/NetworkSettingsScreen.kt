package de.autoapp.android.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
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
import de.autoapp.shared.ui.NetworksUiState
import de.autoapp.shared.ui.NetworksViewModel

/**
 * Selecting charging networks.
 *
 * The list comes from the shipped catalog, not from chargers in the current
 * surroundings. Search is still useful: the catalog runs to dozens of entries,
 * and "ionity" in the search field isolates every variant without touching the
 * rest. Selections are staged — nothing is written to settings until the user
 * taps Confirm.
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
        onNetworkToggled = viewModel::onNetworkToggled,
        onConfirm = viewModel::onConfirm,
        modifier = modifier,
    )
}

@Composable
fun NetworkSettingsScreen(
    uiState: NetworksUiState,
    onSearchChange: (String) -> Unit,
    onNetworkToggled: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 18.dp)) {
        Fineprint(
            text = stringResource(R.string.phone_networks_intro),
            modifier = Modifier.padding(top = 16.dp),
        )

        SearchField(
            value = uiState.search,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.phone_networks_search),
            modifier = Modifier.padding(top = 16.dp),
        )

        if (uiState.networks.isEmpty()) {
            Fineprint(
                text = stringResource(R.string.phone_networks_no_match, uiState.search.trim()),
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            // Laid out lazily: the catalog can run to hundreds of entries.
            AppCard(modifier = Modifier.padding(top = 8.dp)) {
                LazyColumn {
                    itemsIndexed(uiState.networks) { index, network ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        TickRow(
                            label = network.name,
                            checked = network.key in uiState.pending,
                            dotColor = operatorColor(network.name),
                            onClick = { onNetworkToggled(network.key) },
                        )
                    }
                }
            }
        }

        Button(
            onClick = onConfirm,
            enabled = uiState.canConfirm,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Confirm filters")
        }
    }
}
