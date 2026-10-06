package org.julakali.chargeahead.shared

/** A decimal in the current locale's notation ("8,4" / "8.4"), without grouping. */
expect fun formatDecimal(value: Double, fractionDigits: Int): String

/** The language the UI speaks right now, as a BCP 47 tag ("de-DE"). */
expect fun currentLanguageTag(): String
