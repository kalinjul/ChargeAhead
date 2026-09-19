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

    /** How many of [known] the filter keeps; `0` means no filter. Keys no network carries don't count. */
    fun selectedCount(known: List<Network>): Int =
        if (isActive) known.count { it.key in preferredOperators } else 0

    /** The backend's current list, plus selected networks that dropped off it so they can be unticked. */
    fun selectable(known: List<Network>): List<Network> =
        known.filter { it.rank != null || it.key in preferredOperators }
}
