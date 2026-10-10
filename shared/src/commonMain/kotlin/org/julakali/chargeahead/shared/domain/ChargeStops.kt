package org.julakali.chargeahead.shared.domain

/** The corridor list for one search [area]. */
data class ChargeStops(
    val stops: List<ChargeStop>,
    val area: SearchArea,
    val refill: Refill,
    val destination: Destination?,
    val routeStatus: RouteStatus,
    val networkFilterActive: Boolean,
    /** Where the charge level in use came from, or `null` if there is none. */
    val socSource: SoCSourceKind?,
) {
    /** The network refill for [area]; [stops] come from the store throughout. */
    enum class Refill { RUNNING, DONE, FAILED }
}

enum class RouteStatus {
    /** No destination set — the corridor along the direction of travel applies. */
    NONE,

    /** The search runs along the route. */
    ACTIVE,

    /** Destination set, but no trip to it is planned; the search falls back to the direction of travel. */
    UNAVAILABLE,
}
