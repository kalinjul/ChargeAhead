package org.julakali.chargeahead.shared.domain

/** The driver's limits for charging stops in the phone flows (trip planning, "charge now"). */
data class ChargeFilters(
    val minPowerKw: Double = DEFAULT_MIN_POWER_KW,
    /** Browse the slow chargers (< 50 kW) instead of the fast ones. Not persisted. */
    val slowMode: Boolean = false,
) {
    val isDefault: Boolean
        get() = minPowerKw == DEFAULT_MIN_POWER_KW && !slowMode

    companion object {
        const val DEFAULT_MIN_POWER_KW = 150.0
    }
}
