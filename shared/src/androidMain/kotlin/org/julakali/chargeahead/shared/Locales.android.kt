package org.julakali.chargeahead.shared

import java.text.NumberFormat
import java.util.Locale

/** This locale if the UI has strings for its language, otherwise English, so digits never outlive the text. */
fun Locale.forUi(): Locale = if (language in UI_LANGUAGES) this else Locale.ENGLISH

fun formatDecimal(value: Double, fractionDigits: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale.forUi()).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        isGroupingUsed = false
    }.format(value)

actual fun formatDecimal(value: Double, fractionDigits: Int): String =
    formatDecimal(value, fractionDigits, Locale.getDefault())

actual fun currentLanguageTag(): String = Locale.getDefault().forUi().toLanguageTag()
