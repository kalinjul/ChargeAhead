package org.julakali.chargeahead.android.phone

import androidx.compose.ui.graphics.Color

private val OPERATOR_PALETTE = listOf(
    Color(0xFF1A73E8), Color(0xFF188038), Color(0xFFF9AB00),
    Color(0xFFD93025), Color(0xFF9334E6), Color(0xFF12A4AF),
    Color(0xFFE8710A), Color(0xFF7CB342),
)

/** Stable color per operator. */
fun operatorColor(operator: String?): Color {
    if (operator == null) return Color(0xFF5F6368)
    return OPERATOR_PALETTE[(operator.hashCode() and Int.MAX_VALUE) % OPERATOR_PALETTE.size]
}
