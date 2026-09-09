package de.autoapp.shared.domain

object OperatorShortName {

    /** `null` when the operator is unknown or not one of the well-known networks. */
    fun of(operator: String?): String? {
        val key = OperatorKey.of(operator) ?: return null
        return SHORT_NAMES.firstNotNullOfOrNull { (prefix, label) ->
            label.takeIf { key == prefix || key.startsWith("$prefix ") }
        }
    }

    // Keyed by the OperatorKey form ("e on", not "e.on") and matched on the
    // leading words, since sources append the product line to the company
    // ("Aral pulse", "EnBW mobility+"). Keep prefixes non-overlapping.
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
