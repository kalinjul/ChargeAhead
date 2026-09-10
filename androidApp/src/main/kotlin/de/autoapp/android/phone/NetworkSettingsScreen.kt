package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.SearchField
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.android.phone.components.SwitchRow
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
                AppCard(modifier = Modifier.padding(vertical = 8.dp)) {
                    // Not lazy: the flow wraps pills across rows, so there are no
                    // rows to recycle. A few hundred pills is fine.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        uiState.networks.forEach { network ->
                            OperatorPill(
                                name = network.name,
                                selected = network.key in uiState.selected,
                                fill = brandColors[network.key] ?: MaterialTheme.colorScheme.primary,
                                onClick = { onNetworkToggled(network.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A charging network as a pill. Since there are no colour dots any more,
 * selection carries the colour: a selected pill fills with [fill] — the
 * operator's own colour where we know it, plain app-blue otherwise.
 */
@Composable
private fun OperatorPill(
    name: String,
    selected: Boolean,
    fill: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) fill else scheme.surfaceVariant,
        contentColor = if (selected) fill.readableInk() else scheme.onSurface,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** Dark ink on a light fill, white on a dark one — so a brand pill stays legible. */
private fun Color.readableInk(): Color =
    if (luminance() > 0.55f) Color(0xFF202124) else Color.White

/**
 * Brand colours for the networks we recognise, keyed by catalog key. Everything
 * not in here falls back to app-blue. Kept in the UI layer: these are Android
 * [Color]s and only the pills use them.
 */
private val brandColors: Map<String, Color> = mapOf(
    "ionity" to Color(0xFF00C389),
    "tesla" to Color(0xFFE82127),
    "enbw" to Color(0xFF1E3A5F),
    "shell-recharge" to Color(0xFFFBCE07),
    "allego" to Color(0xFFE3000F),
    "fastned" to Color(0xFFFFE500),
    "aral-pulse" to Color(0xFF0060AE),
    "bp-pulse" to Color(0xFF009E49),
    "eon-drive" to Color(0xFFE3001B),
    "totalenergies" to Color(0xFFED1C24),
    "enel-x" to Color(0xFF26CAD3),
    "mer" to Color(0xFF00A499),
    "ewe-go" to Color(0xFF009EE0),
    "pfalzwerke" to Color(0xFFF39200),
)
