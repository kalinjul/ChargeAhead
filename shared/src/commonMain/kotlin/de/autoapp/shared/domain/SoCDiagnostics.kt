package de.autoapp.shared.domain

/**
 * The outcome of the last attempt to read the charge level from the vehicle.
 *
 * Why this is recorded at all: vehicle data is only available *inside*
 * Android Auto — there's a `CarContext` there, but not on the phone. So the
 * driver can't check on the phone whether it's working, even though that's
 * exactly where the settings live. That's why the car session writes its
 * outcome here, and the phone UI reads it.
 *
 * Per ARCHITECTURE.md 1.2, [Outcome.NO_DATA] is the normal case, not the
 * exception: in projection mode, very few head units actually provide these
 * values.
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
