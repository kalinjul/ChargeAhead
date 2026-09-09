package de.autoapp.shared.domain

data class NetworkPreferences(
    val onlyPreferred: Boolean = true,
    /** Normalized keys, see [OperatorKey]. */
    val preferredOperators: Set<String> = emptySet(),
) {
    val isActive: Boolean get() = onlyPreferred && preferredOperators.isNotEmpty()

    fun allows(operator: String?): Boolean {
        if (!isActive) return true
        val key = OperatorKey.of(operator)
        // Don't filter out an unknown operator: the source may simply not
        // know it, and hiding a site is worse than one that turns out to
        // belong to a different network once you're there.
        return key == null || key in preferredOperators
    }
}

data class OperatorOption(
    val key: String,
    val displayName: String,
    val siteCount: Int,
)
