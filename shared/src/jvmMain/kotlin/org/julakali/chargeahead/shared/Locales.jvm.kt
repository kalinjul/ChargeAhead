package org.julakali.chargeahead.shared

import java.text.NumberFormat
import java.util.Locale

actual fun formatDecimal(value: Double, fractionDigits: Int): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        isGroupingUsed = false
    }.format(value)

actual fun currentLanguageTag(): String = Locale.getDefault().toLanguageTag()
