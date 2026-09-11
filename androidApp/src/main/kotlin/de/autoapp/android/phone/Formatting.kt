package de.autoapp.android.phone

import kotlin.math.roundToInt

internal fun Double.oneDecimal(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
