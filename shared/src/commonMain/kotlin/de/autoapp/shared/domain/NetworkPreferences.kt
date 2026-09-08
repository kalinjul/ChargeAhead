package de.autoapp.shared.domain

/**
 * Which charging networks the driver wants to see.
 *
 * [onlyPreferred] is the switch, not the empty set: someone who hasn't
 * selected anything should see everything, and so should someone who enables
 * the filter but selects nothing. Treating an empty list as "show nothing"
 * would be the least friendly possible interpretation.
 *
 * Default on: with no selection it filters nothing anyway, and the first
 * ticked network then takes effect without hunting for a second switch.
 */
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

/** A selectable charging network, as it appears in settings. */
data class OperatorOption(
    val key: String,
    val displayName: String,
    /** How many sites in the current area belong to it. */
    val siteCount: Int,
)
