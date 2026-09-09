package de.autoapp.shared.domain

/**
 * The short label a map marker carries for the best-known charging networks.
 *
 * Unlike [OperatorKey], this *is* a maintained table — and deliberately a
 * tiny one. On a marker there is room for about six characters, so the raw
 * name ("Shell Recharge Solutions (DE)") is unusable, and shortening it
 * mechanically would produce a different wrong answer for every operator.
 * Everything outside the table stays unlabeled: an unrecognized network shows
 * only its bolts, which is honest, rather than a guessed abbreviation.
 *
 * The table shortens; it never merges. Two operators that [OperatorKey] keeps
 * apart keep their own entries or none at all.
 */
object OperatorShortName {

    /** `null` when the operator is unknown or not one of the well-known networks. */
    fun of(operator: String?): String? {
        val key = OperatorKey.of(operator) ?: return null
        return SHORT_NAMES.firstNotNullOfOrNull { (prefix, label) ->
            label.takeIf { key == prefix || key.startsWith("$prefix ") }
        }
    }

    /**
     * Keyed by the [OperatorKey] form — lowercase, legal form and punctuation
     * gone, which is why "e.on" appears as "e on". Matched on the leading
     * words, because the sources append the product line to the company:
     * "Aral pulse", "EnBW mobility+", "Shell Recharge Solutions".
     *
     * Order matters only if two prefixes overlap; today none do.
     */
    private val SHORT_NAMES = listOf(
        "ewe go" to "EWE Go",
        "enbw" to "EnBW",
        "tesla" to "Tesla",
        "aral" to "Aral",
        "shell" to "Shell",
        "e on" to "E.ON",
        "ionity" to "Ionity",
    )
}
