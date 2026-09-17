package org.julakali.chargeahead.shared.domain

/**
 * The outcome of the last attempt to read the charge level from the vehicle.
 * Written by the car session, read by the phone UI.
 */
data class SoCDiagnostics(
    val checkedAtMillis: Long,
    val outcome: Outcome,
    /** Extra detail for display, e.g. the status code or the value read. */
    val detail: String? = null,
) {
    enum class Outcome {
        /** The host offers no vehicle data at all. */
        NO_CAR_HARDWARE,

        /** `com.google.android.gms.permission.CAR_FUEL` was not granted. */
        NO_PERMISSION,

        /** Registered, but the head unit returns no value. The normal case. */
        NO_DATA,

        /** A charge level came through. */
        AVAILABLE,
    }
}
