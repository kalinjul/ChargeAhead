package org.julakali.chargeahead.android.phone

import androidx.compose.ui.graphics.Color
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.NetworkCatalog
import org.julakali.chargeahead.shared.domain.OperatorKey

/** Brand colours for the networks we recognise, keyed by catalog key. One table for filter, lists, markers. */
private val BRAND_COLORS: Map<String, Color> = mapOf(
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

private val PALETTE = listOf(
    Color(0xFF1A73E8), Color(0xFF188038), Color(0xFFF9AB00),
    Color(0xFFD93025), Color(0xFF9334E6), Color(0xFF12A4AF),
    Color(0xFFE8710A), Color(0xFF7CB342), Color(0xFF00897B),
    Color(0xFF8E24AA), Color(0xFF3949AB), Color(0xFFC0CA33),
)

private val UNKNOWN = Color(0xFF5F6368)

/** The network's brand colour, or a stable one from the palette. */
fun networkColor(key: String): Color = BRAND_COLORS[key] ?: PALETTE[(key.hashCode() and Int.MAX_VALUE) % PALETTE.size]

/** A site wears its network's colour; a site of no known network gets a stable colour from its operator name. */
fun operatorColor(site: ChargeSite): Color {
    val key = site.networkKey ?: NetworkCatalog.resolve(site)
    if (key != null) return networkColor(key)
    val operator = OperatorKey.of(site.operator) ?: return UNKNOWN
    return PALETTE[(operator.hashCode() and Int.MAX_VALUE) % PALETTE.size]
}
