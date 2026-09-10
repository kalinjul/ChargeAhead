package de.autoapp.shared.domain

/**
 * The driver's hard limits for charging stops, next to [NetworkPreferences].
 *
 * These apply to the phone flows (trip planning, "charge now"); the car list
 * keeps its own, deliberately simpler rules. Availability ("only free right
 * now") is intentionally not a filter: no connected source delivers live
 * status yet, and pretending to filter by it would be a lie in the UI. Price
 * is not one either, and for the same reason — see ROADMAP open point 10.
 */
data class ChargeFilters(
    val minPowerKw: Double = DEFAULT_MIN_POWER_KW,
    /** Radius for "charge now", not for route planning. */
    val maxDistanceKm: Double = DEFAULT_MAX_DISTANCE_KM,
) {
    val isDefault: Boolean
        get() = minPowerKw == DEFAULT_MIN_POWER_KW && maxDistanceKm == DEFAULT_MAX_DISTANCE_KM

    companion object {
        const val DEFAULT_MIN_POWER_KW = 150.0
        const val DEFAULT_MAX_DISTANCE_KM = 5.0
    }
}
