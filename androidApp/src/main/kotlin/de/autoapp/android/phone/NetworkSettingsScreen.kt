package de.autoapp.android.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.SearchField
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.android.phone.components.SwitchRow
import de.autoapp.android.phone.components.TickRow
import de.autoapp.shared.ui.NetworksUiState
import de.autoapp.shared.ui.NetworksViewModel

/**
 * Selecting charging networks.
 *
 * The list comes from the shipped catalog, not from chargers in the current
 * surroundings. Search is still useful: the catalog runs to dozens of entries,
 * and "ionity" in the search field isolates every variant without touching the
 * rest.
 *
 * There is no confirm button: leaving the screen applies the edits. The
 * ViewModel stages them until then, so a handful of ticks costs one replan
 * instead of one per tick.
 */
@Composable
fun NetworksRoute(
    modifier: Modifier = Modifier,
    viewModel: NetworksViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Leaving the composition is the commit — that covers the back arrow,
    // the system back gesture and the drawer alike, which a callback on the
    // back arrow alone would not.
    DisposableEffect(viewModel) {
        onDispose { viewModel.onLeave() }
    }

    NetworkSettingsScreen(
        uiState = uiState,
        onSearchChange = viewModel::onSearchChanged,
        onNetworkToggled = viewModel::onNetworkToggled,
        onOnlyPreferredChange = viewModel::onOnlyPreferredChanged,
        modifier = modifier,
    )
}

@Composable
fun NetworkSettingsScreen(
    uiState: NetworksUiState,
    onSearchChange: (String) -> Unit,
    onNetworkToggled: (String) -> Unit,
    onOnlyPreferredChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 18.dp)) {
        AppCard(modifier = Modifier.padding(top = 16.dp)) {
            // The switch reads the other way round from the setting it writes:
            // browsing is the state without a filter, so it is on exactly when
            // onlyPreferred is off.
            SwitchRow(
                label = stringResource(R.string.phone_networks_browse),
                sublabel = stringResource(R.string.phone_networks_browse_hint),
                checked = !uiState.onlyPreferred,
                onCheckedChange = { browsing -> onOnlyPreferredChange(!browsing) },
            )
        }

        SectionLabel(
            text = stringResource(R.string.phone_networks_mine),
            modifier = Modifier.padding(top = 20.dp),
        )
        Fineprint(text = stringResource(R.string.phone_networks_intro))

        SearchField(
            value = uiState.search,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.phone_networks_search),
            modifier = Modifier.padding(top = 16.dp),
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.networks.isEmpty()) {
                Fineprint(
                    text = stringResource(R.string.phone_networks_no_match, uiState.search.trim()),
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                // Laid out lazily: the catalog can run to hundreds of entries.
                AppCard(modifier = Modifier.padding(vertical = 8.dp)) {
                    LazyColumn {
                        itemsIndexed(uiState.networks) { index, network ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            TickRow(
                                label = network.name,
                                checked = network.key in uiState.selected,
                                onClick = { onNetworkToggled(network.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}
