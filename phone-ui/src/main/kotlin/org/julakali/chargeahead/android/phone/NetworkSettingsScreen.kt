package org.julakali.chargeahead.android.phone

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.components.Fineprint
import org.julakali.chargeahead.android.phone.components.LazyFlowRow
import org.julakali.chargeahead.android.phone.components.SearchField
import org.julakali.chargeahead.android.phone.components.SectionLabel
import org.julakali.chargeahead.android.phone.components.SwitchRow
import org.julakali.chargeahead.shared.ui.NetworksUiState
import org.julakali.chargeahead.shared.ui.NetworksViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Selecting charging networks from the shipped catalog. Leaving the screen
 * applies the edits.
 */
@Composable
fun NetworksRoute(
    modifier: Modifier = Modifier,
    viewModel: NetworksViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Entering freezes the pill order; leaving the composition commits.
    DisposableEffect(viewModel) {
        viewModel.onEnter()
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
            // On exactly when onlyPreferred is off.
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
                AppCard(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                    // One continuous flow; only the lines on screen compose.
                    LazyFlowRow(
                        horizontalSpacing = 9.dp,
                        verticalSpacing = 9.dp,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        // Keyed so a pill's animation state stays with it as the list filters.
                        items(uiState.networks, key = { it.key }) { network ->
                            OperatorPill(
                                name = network.name,
                                selected = network.key in uiState.selected,
                                fill = networkColor(network.key),
                                onClick = { onNetworkToggled(network.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A charging network as a pill; a selected pill fills with [fill]. */
@Composable
private fun OperatorPill(
    name: String,
    selected: Boolean,
    fill: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Fill and ink crossfade together.
    val container by animateColorAsState(
        targetValue = if (selected) fill else scheme.surfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "pillContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) fill.readableInk() else scheme.onSurface,
        animationSpec = tween(durationMillis = 200),
        label = "pillContent",
    )
    // A tap dents the pill in and springs it back. Driven off the click, not the press state.
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    Surface(
        onClick = {
            onClick()
            scope.launch {
                scale.snapTo(0.9f)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
        },
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier
            // So a screen reader — and a test — can tell a picked network from the rest.
            .semantics { this.selected = selected }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** Dark ink on a light fill, white on a dark one. */
private fun Color.readableInk(): Color =
    if (luminance() > 0.55f) Color(0xFF202124) else Color.White

