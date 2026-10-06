package org.julakali.chargeahead.android.phone.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** One placeholder bar, painted with the shared sweeping [shimmer] brush. */
@Composable
fun SkeletonBar(shimmer: Brush, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.background(shimmer, shape))
}

/** A light band sweeping left-to-right across the placeholders; one brush drives every bar. */
@Composable
fun rememberShimmerBrush(): Brush {
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
