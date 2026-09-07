package de.autoapp.shared.domain

/**
 * One data point from the car hardware, for the phone's debug view.
 *
 * Same split as [SoCDiagnostics]: captured in the car session (only there is
 * a `CarContext`), persisted through the settings store, displayed on the
 * phone. Values are pre-formatted strings — this is a diagnostic surface, not
 * a data model to compute on; anything the app computes with has its own
 * typed path (EnergyState).
 */
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

    /** Listener registered, but the head unit reports no value (the normal case). */
    NO_DATA,

    /** This host offers no car-hardware service at all. */
    NO_CAR_HARDWARE,
}
