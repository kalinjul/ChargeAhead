package org.julakali.chargeahead.shared

/** The languages composeResources has strings for; any other one falls back to English. */
val UI_LANGUAGES = setOf("en", "de")

/** A decimal in the UI language's notation ("8,4" / "8.4"), without grouping. */
expect fun formatDecimal(value: Double, fractionDigits: Int): String

/** The language the UI speaks right now, as a BCP 47 tag ("de-DE"); English when the device's isn't supported. */
expect fun currentLanguageTag(): String
