package org.julakali.chargeahead.shared.domain

/** One data point from the car hardware, for the phone's debug view. */
data class CarDataPoint(
    val kind: CarDataKind,
    val status: CarDataStatus,
    /** Human-readable value, `null` unless [status] is [CarDataStatus.AVAILABLE]. */
    val value: String?,
    val observedAtMillis: Long,
)

enum class CarDataKind {
    MODEL,
    ENERGY_PROFILE,
    BATTERY_PERCENT,
    RANGE,
    ENERGY_IS_LOW,
    SPEED,
    ODOMETER,
}

enum class CarDataStatus {
    AVAILABLE,

    /** The car permission wasn't granted — fixable by the driver. */
    NO_PERMISSION,

    /** Listener registered, but the head unit reports no value. */
    NO_DATA,

    /** This host offers no car-hardware service at all. */
    NO_CAR_HARDWARE,
}
