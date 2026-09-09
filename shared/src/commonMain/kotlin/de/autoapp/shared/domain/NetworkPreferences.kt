package de.autoapp.shared.domain

data class NetworkPreferences(
    val onlyPreferred: Boolean = true,
    /** Catalog keys, see [NetworkCatalog]. */
    val preferredOperators: Set<String> = emptySet(),
) {
    val isActive: Boolean get() = onlyPreferred && preferredOperators.isNotEmpty()

    fun allowsSite(site: ChargeSite): Boolean {
        if (!isActive) return true
        // null → not in catalog → hidden when filter is active.
        return NetworkCatalog.resolve(site) in preferredOperators
    }

    fun selectedNetworks(): List<Network> =
        if (isActive) NetworkCatalog.selection(preferredOperators) else emptyList()
}

data class OperatorOption(
    val key: String,
    val displayName: String,
    val siteCount: Int,
)
