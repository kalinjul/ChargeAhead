package org.julakali.chargeahead.android.phone

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.components.AppCard
import org.julakali.chargeahead.android.phone.components.SectionLabel
import org.julakali.chargeahead.android.phone.components.StationCard
import org.julakali.chargeahead.android.phone.components.sheetListPadding
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.domain.ChargeNowCandidate
import org.julakali.chargeahead.shared.domain.RelaxedFilter
import org.julakali.chargeahead.shared.ui.ChargeNowUiState
import org.julakali.chargeahead.shared.ui.ChargeNowViewModel
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun ChargeNowRoute(
    onNavigate: (ChargeNowCandidate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChargeNowViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // The sheet exists only while open: rank from wherever the driver is now.
    LaunchedEffect(viewModel) { viewModel.onSheetOpened() }
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
                // The whole sheet as a skeleton, so it is already at its final size.
                val shimmer = rememberShimmerBrush()
                SkeletonBar(shimmer, Modifier.fillMaxWidth(0.5f).height(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    repeat(3) { ChargeNowSkeletonCard(shimmer) }
                    SkeletonBar(shimmer, Modifier.padding(top = 8.dp).fillMaxWidth(0.3f).height(10.dp))
                    repeat(5) { ChargeNowSkeletonCard(shimmer) }
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
        badgeColor = operatorColor(candidate.site),
        title = candidate.site.operator ?: candidate.site.name,
        metaLine = stringResource(R.string.cn_distance_power, candidate.distanceKm.oneDecimal(), candidate.maxPowerKw.roundToInt()),
        address = ChargeStopFormatter.addressLine(candidate.site),
        onSend = { onNavigate(candidate) },
        sendContentDescription = stringResource(R.string.cn_navigate, candidate.site.name),
    )
}

private fun RelaxedFilter.labelRes(): Int = when (this) {
    RelaxedFilter.MIN_POWER -> R.string.cn_relax_min_power
    RelaxedFilter.NETWORKS -> R.string.cn_relax_networks
    RelaxedFilter.MAX_DISTANCE -> R.string.cn_relax_max_distance
}

/** A placeholder card mirroring [StationCard]'s shape while the ranking runs. */
@Composable
private fun ChargeNowSkeletonCard(shimmer: Brush) {
    AppCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
        ) {
            SkeletonBar(shimmer, Modifier.size(24.dp), shape = CircleShape)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBar(shimmer, Modifier.fillMaxWidth(0.55f).height(15.dp))
                SkeletonBar(shimmer, Modifier.fillMaxWidth(0.35f).height(12.dp))
                SkeletonBar(shimmer, Modifier.fillMaxWidth(0.7f).height(12.dp))
            }
        }
    }
}

/** One placeholder bar, painted with the shared sweeping [shimmer] brush. */
@Composable
private fun SkeletonBar(shimmer: Brush, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.background(shimmer, shape))
}

/** A light band sweeping left-to-right across the placeholders; one brush drives every bar. */
@Composable
private fun rememberShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -SHIMMER_WIDTH,
        targetValue = SHIMMER_WIDTH * 2,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + SHIMMER_WIDTH, 0f),
    )
}

/** Sweep-band width in px. */
private const val SHIMMER_WIDTH = 280f
