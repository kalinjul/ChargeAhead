package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.autoapp.android.R
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.android.phone.components.StationCard
import de.autoapp.android.phone.components.sheetListPadding
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.core.ChargeNowCandidate
import de.autoapp.shared.core.RelaxedFilter
import de.autoapp.shared.ui.ChargeNowUiState
import de.autoapp.shared.ui.ChargeNowViewModel
import kotlin.math.roundToInt

@Composable
fun ChargeNowRoute(
    onNavigate: (ChargeNowCandidate) -> Unit,
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
    onNavigate: (ChargeNowCandidate) -> Unit,
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
                val context = LocalContext.current
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
    candidate: ChargeNowCandidate,
    onNavigate: (ChargeNowCandidate) -> Unit,
) {
    StationCard(
        rank = rank,
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
