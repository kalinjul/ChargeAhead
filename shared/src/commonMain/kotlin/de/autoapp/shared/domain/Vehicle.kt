package de.autoapp.shared.domain

/**
 * The vehicle calculations are done for (M2).
 *
 * Consumption is a fixed value, not a model: factoring in temperature, speed
 * and elevation profile only pays off once the estimate proves too coarse in
 * practice (ARCHITECTURE.md, open item 3). A rolling average from actual SoC
 * drop could be added from M4 onward — though that requires reliable
 * measurements and is useless with manual entry.
 */
data class VehicleProfile(
    val displayName: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double,
    val acceptedConnectors: Set<ConnectorType>,
    /**
     * DC charging peak, for charge-time estimates in trip planning. Added at
     * the end with a default so existing callers and stored profiles stay
     * valid; `null` means "unknown" and the site's connector power is used
     * alone.
     */
    val dcPeakPowerKw: Double? = null,
) {
    init {
        require(usableBatteryKwh > 0.0) { "usableBatteryKwh must be positive" }
        require(consumptionKwhPer100Km > 0.0) { "consumptionKwhPer100Km must be positive" }
    }
}

/** Where the charge level comes from. Determines how much to trust it. */
enum class SoCSourceKind {
    /** Typed in by the driver — the only source that works on both platforms. */
    MANUAL,

    /** From the head unit via the Car App Library. Rarely available, but accurate when it is. */
    CAR_HARDWARE,

    /** Via an OEM cloud service. Not yet connected. */
    OEM_CLOUD,
}

/** A charge level with its source and timestamp. */
data class EnergyState(
    val socPercent: Double,
    val source: SoCSourceKind,
    val observedAtMillis: Long,
)

/**
 * Share of the battery that's never planned into range calculations.
 *
 * Planning down to the last kilowatt-hour would mean sending the driver to a
 * charging site with no buffer, and that site could be occupied or broken.
 */
const val DEFAULT_RESERVE_SOC_PERCENT = 10.0
