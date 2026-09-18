package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.SoCSourceKind

/**
 * What the car UI should currently display — list *and* status.
 *
 * A flat type instead of a sealed hierarchy because it crosses into Swift.
 *
 * [stops] stays populated during [Phase.LOADING] and [Phase.FAILED].
 */
data class ChargeStopsState(
    val stops: List<ChargeStop> = emptyList(),
    val phase: Phase = Phase.WAITING_FOR_LOCATION,
    val failure: FailureReason? = null,
    /** The list came from [org.julakali.chargeahead.shared.data.DemoSiteSource], not real data. */
    val isDemo: Boolean = false,
    /** Where the charge level in use came from, or `null` if there is none. */
    val socSource: SoCSourceKind? = null,
    /** The set destination, or `null` if searching along the direction of travel. */
    val destination: Destination? = null,
    val routeStatus: RouteStatus = RouteStatus.NONE,
    /** The charging-network filter is currently active. */
    val networkFilterActive: Boolean = false,
    /** Last computed position. `null` before the first fix. */
    val position: org.julakali.chargeahead.shared.domain.LatLon? = null,
) {
    enum class RouteStatus {
        /** No destination set — the corridor along the direction of travel applies. */
        NONE,

        /** Destination set, route is being computed. */
        CALCULATING,

        /** The search runs along the route. */
        ACTIVE,

        /** Destination set, but no route could be obtained; the search falls back to the direction of travel. */
        UNAVAILABLE,
    }

    enum class Phase {
        /** No location yet. */
        WAITING_FOR_LOCATION,

        /** Search area is set, the source is still responding. */
        LOADING,

        /** [stops] is current. Empty here genuinely means: no charge site ahead. */
        READY,

        /** Something went wrong; [failure] says what. */
        FAILED,
    }

    enum class FailureReason {
        /** No location — usually missing permission or location services turned off. */
        LOCATION_UNAVAILABLE,

        /** The charge-site source was unreachable or reported an error. */
        SITES_UNAVAILABLE,
    }
}
