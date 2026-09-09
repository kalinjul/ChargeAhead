package de.autoapp.android.phone

import kotlin.math.roundToInt

internal fun Double.oneDecimal(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

/** "0.49" → "0,49": prices are user-visible text and therefore German. */
internal fun Double.twoDecimals(): String {
    val cents = (this * 100).toInt()
    return "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
}
