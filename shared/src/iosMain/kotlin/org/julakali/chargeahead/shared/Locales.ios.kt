package org.julakali.chargeahead.shared

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.preferredLanguages

actual fun formatDecimal(value: Double, fractionDigits: Int): String =
    NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = fractionDigits.toULong()
        maximumFractionDigits = fractionDigits.toULong()
        usesGroupingSeparator = false
        locale = NSLocale.currentLocale.takeIf { it.languageCode in UI_LANGUAGES } ?: NSLocale(localeIdentifier = "en")
    }.stringFromNumber(NSNumber(value)) ?: value.toString()

actual fun currentLanguageTag(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)
        ?.takeIf { it.substringBefore('-') in UI_LANGUAGES }
        ?: "en"
