package org.julakali.chargeahead.shared.domain

data class NetworkPreferences(
    val onlyPreferred: Boolean = true,
    /** Network keys, as the backend assigns them to sites. */
    val preferredOperators: Set<String> = emptySet(),
) {
    val isActive: Boolean get() = onlyPreferred && preferredOperators.isNotEmpty()

    fun allowsSite(site: ChargeSite): Boolean {
        if (!isActive) return true
        // null → no known network → hidden when filter is active.
        return site.networkKey in preferredOperators
    }

    /** The keys to fetch with; empty means every network. */
    fun selectedKeys(): Set<String> = if (isActive) preferredOperators else emptySet()

    /** How many networks the filter keeps; `0` means no filter. */
    fun selectedCount(): Int = if (isActive) preferredOperators.size else 0

    /**
     * The backend's current list, plus every selected network off it, so each
     * one that filters can be seen and unticked. One [known] never heard of
     * shows under its key.
     */
    fun selectable(known: List<Network>): List<Network> {
        val knownKeys = known.mapTo(HashSet()) { it.key }
        val unknown = preferredOperators.filterNot { it in knownKeys }.map { Network(key = it, name = it) }
        return known.filter { it.rank != null || it.key in preferredOperators } + unknown
    }
}
