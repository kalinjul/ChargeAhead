package org.julakali.chargeahead.android.phone

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.roundToInt

/** "3,5" in German, "3.5" elsewhere; whole numbers lose the decimal ("5"). */
@Composable
internal fun Double.oneDecimal(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    if (rounded == rounded.toLong().toDouble()) return rounded.toLong().toString()
    return String.format(LocalConfiguration.current.locales[0], "%.1f", rounded)
}
