package org.julakali.chargeahead.shared.domain

/** A charger as the map shows it: the site and its strongest DC power. */
data class MapCharger(
    val site: ChargeSite,
    val maxPowerKw: Double,
    /** `null` until live data was found for the site. */
    val availability: SiteAvailability? = null,
)

/** The part of the driver's settings the map filters by. */
data class MapFilter(
    val networks: NetworkPreferences,
    val minPowerKw: Double,
    val slowMode: Boolean,
) {
    companion object {
        fun of(filters: ChargeFilters, networks: NetworkPreferences) =
            MapFilter(networks, filters.minPowerKw, filters.slowMode)
    }
}
