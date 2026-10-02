package org.julakali.chargeahead.shared.domain

/**
 * The browsing mode the driver has switched on: AC posts instead of fast
 * chargers, or every network instead of the picked ones. At most one; the two
 * together would cancel each other's point, so switching one on switches the other off.
 */
enum class ChargeMode {
    NORMAL,
    AC,
    BROWSE,
    ;

    companion object {
        /** Both on can only come from an older preference file; AC wins, it is the louder one. */
        fun of(filters: ChargeFilters, networks: NetworkPreferences): ChargeMode = when {
            filters.slowMode -> AC
            !networks.onlyPreferred -> BROWSE
            else -> NORMAL
        }
    }
}
