package org.julakali.chargeahead.android.phone

import androidx.compose.ui.graphics.Color
import org.julakali.chargeahead.shared.domain.OperatorKey

/** Brand-near colours for the networks a German trip actually meets; keyed like [OperatorKey.of]. */
private val BRAND_COLORS = mapOf(
    "ionity" to Color(0xFF12A4AF),
    "enbw" to Color(0xFFE8710A),
    "enbw mobility" to Color(0xFFE8710A),
    "aral pulse" to Color(0xFF1A73E8),
    "aral" to Color(0xFF1A73E8),
    "tesla" to Color(0xFFD93025),
    "fastned" to Color(0xFFF9AB00),
    "allego" to Color(0xFF3F51B5),
    "shell recharge" to Color(0xFFF9AB00),
    "ewe go" to Color(0xFF7CB342),
    "mer" to Color(0xFF188038),
    "e on" to Color(0xFFE00000),
    "eon" to Color(0xFFE00000),
    "eon drive" to Color(0xFFE00000),
    "pfalzwerke" to Color(0xFF9334E6),
    "lidl" to Color(0xFF0050AA),
    "kaufland" to Color(0xFFE10915),
    "total energies" to Color(0xFFE4002B),
    "totalenergies" to Color(0xFFE4002B),
    "vattenfall" to Color(0xFF2071B5),
    "maingau" to Color(0xFF006AB3),
    "ladenetz" to Color(0xFF4E9A2F),
    "eweg" to Color(0xFF7CB342),
    "comfortcharge" to Color(0xFF5E35B1),
    "autobahn tank rast" to Color(0xFF00695C),
    "elli" to Color(0xFF6A1B9A),
    "volkswagen" to Color(0xFF6A1B9A),
    "mercedes benz" to Color(0xFF546E7A),
    "bp pulse" to Color(0xFF1A73E8),
)

private val OPERATOR_PALETTE = listOf(
    Color(0xFF1A73E8), Color(0xFF188038), Color(0xFFF9AB00),
    Color(0xFFD93025), Color(0xFF9334E6), Color(0xFF12A4AF),
    Color(0xFFE8710A), Color(0xFF7CB342), Color(0xFF00897B),
    Color(0xFF8E24AA), Color(0xFF3949AB), Color(0xFFC0CA33),
)

/** Stable colour per operator: the brand's where we know it, a hash otherwise. */
fun operatorColor(operator: String?): Color {
    val key = OperatorKey.of(operator) ?: return Color(0xFF5F6368)
    BRAND_COLORS[key]?.let { return it }
    BRAND_COLORS.entries.firstOrNull { key.startsWith(it.key + " ") }?.let { return it.value }
    return OPERATOR_PALETTE[(key.hashCode() and Int.MAX_VALUE) % OPERATOR_PALETTE.size]
}
