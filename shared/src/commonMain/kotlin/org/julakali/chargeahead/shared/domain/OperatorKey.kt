package org.julakali.chargeahead.shared.domain

/**
 * Normalizes operator names so the same provider can be recognized as such.
 * Only decoration is stripped: legal form and country suffix,
 * e.g. "IONITY GmbH" → "ionity".
 */
object OperatorKey {

    /** `null` when no operator is known, or when only decoration would remain. */
    fun of(operator: String?): String? {
        val trimmed = operator?.trim()?.lowercase() ?: return null
        if (trimmed.isEmpty()) return null

        // Source placeholders aren't an operator (see OpenChargeMapSource).
        if (trimmed.startsWith("(")) return null

        var key = trimmed
        // Strip parenthesized suffixes like "(d)" or "(de)".
        key = key.replace(PARENTHESISED, " ")
        LEGAL_FORMS.forEach { form -> key = key.replace(form, " ") }
        key = key.replace(PUNCTUATION, " ").replace(WHITESPACE, " ").trim()

        return key.takeIf { it.isNotEmpty() }
    }

    /** Display name for a key: the shortest observed name wins. */
    fun displayName(operators: Collection<String>): String? =
        operators.filter { it.isNotBlank() }.minByOrNull { it.length }

    /** Name without case distinction and without umlauts — for sorting and searching. */
    fun folded(displayName: String): String = displayName
        .lowercase()
        .replace("ä", "a")
        .replace("ö", "o")
        .replace("ü", "u")
        .replace("ß", "ss")

    private val PARENTHESISED = Regex("""\([^)]*\)""")
    private val PUNCTUATION = Regex("""[.,&+\-–/]""")
    private val WHITESPACE = Regex("""\s+""")

    /** Legal forms and suffixes that don't distinguish operators. */
    private val LEGAL_FORMS = listOf(
        Regex("""\bgmbh\b"""),
        Regex("""\bag\b"""),
        Regex("""\bkg\b"""),
        Regex("""\bco\b"""),
        Regex("""\bse\b"""),
        Regex("""\be\.?k\.?\b"""),
        Regex("""\be\.?v\.?\b"""),
        Regex("""\bmbh\b"""),
        Regex("""\bug\b"""),
    )
}
