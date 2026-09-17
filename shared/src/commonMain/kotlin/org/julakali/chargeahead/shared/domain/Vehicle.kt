package org.julakali.chargeahead.shared.domain

/** The vehicle calculations are done for. */
data class VehicleProfile(
    val displayName: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double,
    val acceptedConnectors: Set<ConnectorType>,
    /** DC charging peak; `null` means unknown and the site's connector power is used alone. */
    val dcPeakPowerKw: Double? = null,
) {
    init {
        require(usableBatteryKwh > 0.0) { "usableBatteryKwh must be positive" }
        require(consumptionKwhPer100Km > 0.0) { "consumptionKwhPer100Km must be positive" }
    }
}

/** Where the charge level comes from. Determines how much to trust it. */
enum class SoCSourceKind {
    /** Typed in by the driver. */
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

/** Share of the battery that's never planned into range calculations. */
const val DEFAULT_RESERVE_SOC_PERCENT = 10.0

/** Default charge level to still have when arriving at the destination. */
const val DEFAULT_ARRIVAL_SOC_PERCENT = DEFAULT_RESERVE_SOC_PERCENT

/** The highest arrival level worth offering. */
const val MAX_ARRIVAL_SOC_PERCENT = 80.0
