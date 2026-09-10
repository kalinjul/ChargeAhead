package de.autoapp.shared.domain

/**
 * The driver's hard limits for charging stops, next to [NetworkPreferences].
 *
 * These apply to the phone flows (trip planning, "charge now"); the car list
 * keeps its own, deliberately simpler rules. Availability ("only free right
 * now") is intentionally not a filter: no connected source delivers live
 * status yet, and pretending to filter by it would be a lie in the UI.
 */
data class ChargeFilters(
    val minPowerKw: Double = DEFAULT_MIN_POWER_KW,
    val maxPriceEuroPerKwh: Double = DEFAULT_MAX_PRICE,
    /** Radius for "charge now", not for route planning. */
    val maxDistanceKm: Double = DEFAULT_MAX_DISTANCE_KM,
    /**
     * Browse the slow chargers (< 50 kW) instead of the fast ones: the map
     * then shows only those, from every network, ignoring the other filters.
     * A transient view mode — not persisted, so a restart lands back on fast.
     */
    val slowMode: Boolean = false,
) {
    val isDefault: Boolean
        get() = minPowerKw == DEFAULT_MIN_POWER_KW &&
            maxPriceEuroPerKwh == DEFAULT_MAX_PRICE &&
            maxDistanceKm == DEFAULT_MAX_DISTANCE_KM &&
            !slowMode

    companion object {
        const val DEFAULT_MIN_POWER_KW = 150.0
        const val DEFAULT_MAX_PRICE = 0.85
        const val DEFAULT_MAX_DISTANCE_KM = 5.0
    }
}
