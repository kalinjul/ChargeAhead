package de.autoapp.shared.domain

/**
 * Normalizes operator names so the same provider can be recognized as such.
 *
 * Sources spell it differently — observed in real data: "IONITY GmbH" and
 * "Ionity", "E.ON Drive GmbH" and "E.ON Drive Infrastructure GmbH", "EnBW
 * (D)", "Shell Recharge Solutions (DE)", "Mer Germany GmbH", "Josef Geyer
 * e-mobil GmbH & Co. KG". Without normalization, the same provider would
 * appear multiple times in the selection list, and filtering on "Ionity"
 * would drop half of the Ionity sites.
 *
 * Deliberately **no** maintained mapping table: that would have the same
 * upkeep problem as the vehicle list, and wrongly merging two operators
 * would be worse than showing two separate entries. Only what's demonstrably
 * decoration is stripped — legal form and country suffix.
 */
object OperatorKey {

    /** `null` when no operator is known, or when only decoration would remain. */
    fun of(operator: String?): String? {
        val trimmed = operator?.trim()?.lowercase() ?: return null
        if (trimmed.isEmpty()) return null

        // Source placeholders aren't an operator (see OpenChargeMapSource).
        if (trimmed.startsWith("(")) return null

        var key = trimmed
        // Strip parenthesized suffixes like "(d)" or "(de)" — they
        // distinguish national subsidiaries, not charging networks.
        key = key.replace(PARENTHESISED, " ")
        LEGAL_FORMS.forEach { form -> key = key.replace(form, " ") }
        key = key.replace(PUNCTUATION, " ").replace(WHITESPACE, " ").trim()

        return key.takeIf { it.isNotEmpty() }
    }

    /**
     * Display name for a key: the shortest observed name wins.
     *
     * "Ionity" reads better in the car than "IONITY GmbH", and shorter is a
     * reliable proxy here for less decoration.
     */
    fun displayName(operators: Collection<String>): String? =
        operators.filter { it.isNotBlank() }.minByOrNull { it.length }

    /**
     * Name without case distinction and without umlauts — for sorting and
     * for searching in the selection list.
     *
     * For sorting, because by character code "Ökostrom" would sort after
     * "Zunder" ('ö' comes after 'z'), while someone scanning an alphabetical
     * list looks under O. For searching, the same reasoning from the other
     * side: someone typing "okostrom" means "Ökostrom" and should find it.
     */
    fun folded(displayName: String): String = displayName
        .lowercase()
        .replace("ä", "a")
        .replace("ö", "o")
        .replace("ü", "u")
        .replace("ß", "ss")

    private val PARENTHESISED = Regex("""\([^)]*\)""")
    private val PUNCTUATION = Regex("""[.,&+\-–/]""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * Legal forms and suffixes that don't distinguish operators. Only what's
     * unambiguously decoration — "infrastructure", for instance, is left
     * alone, because "E.ON Drive" and "E.ON Drive Infrastructure" could be
     * different legal entities, and the sources don't clarify that.
     */
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
