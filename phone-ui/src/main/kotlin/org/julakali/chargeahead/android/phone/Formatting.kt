package org.julakali.chargeahead.android.phone

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.roundToInt
import org.julakali.chargeahead.shared.formatDecimal

/** "3,5" in German, "3.5" in English; whole numbers lose the decimal ("5"). */
@Composable
internal fun Double.oneDecimal(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    if (rounded == rounded.toLong().toDouble()) return rounded.toLong().toString()
    return formatDecimal(rounded, 1, LocalConfiguration.current.locales[0])
}
