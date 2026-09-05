package de.autoapp.shared

import de.autoapp.shared.domain.ChargeStop
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.OperatorOption
import de.autoapp.shared.domain.SoCSourceKind

/**
 * What the car UI should currently display — list *and* status.
 *
 * A flat type instead of a sealed hierarchy because it crosses into Swift via
 * the Objective-C header: sealed classes arrive there as a loose collection of
 * subclasses whose exhaustiveness the compiler can no longer check. A data
 * class with an enum carries across without loss.
 *
 * [stops] is deliberately kept populated during [Phase.LOADING] and
 * [Phase.FAILED]: the last known list is worth more than an empty one in a
 * dead zone. The UI keeps showing it and labels the state alongside it.
 *
 * No text here: [Phase] and [FailureReason] are states, not messages. The
 * actual copy lives in `strings.xml` / `Localizable.strings` — each platform
 * localizes on its own (AGENTS.md, language rule).
 */
data class ChargeStopsState(
    val stops: List<ChargeStop> = emptyList(),
    val phase: Phase = Phase.WAITING_FOR_LOCATION,
    val failure: FailureReason? = null,
    /** The list came from [de.autoapp.shared.data.DemoSiteSource], not real data. */
    val isDemo: Boolean = false,
    /**
     * Where the charge level in use came from, or `null` if there is none.
     *
     * The UI needs this: if the vehicle supplies the value, the manual-entry
     * field must be locked — otherwise the driver types in a number that gets
     * silently overwritten.
     */
    val socSource: SoCSourceKind? = null,
    /** The set destination, or `null` if searching along the direction of travel. */
    val destination: Destination? = null,
    val routeStatus: RouteStatus = RouteStatus.NONE,
    /**
     * The charging networks in the current area, **before** the filter.
     *
     * This way the choices offered in settings come from the data itself, not
     * from a maintained list — which would be incomplete with every new provider.
     */
    val availableOperators: List<OperatorOption> = emptyList(),
    /** The charging-network filter is currently active. Explains a short or empty list. */
    val networkFilterActive: Boolean = false,
) {
    enum class RouteStatus {
        /** No destination set — the corridor along the direction of travel applies. */
        NONE,

        /** Destination set, route is being computed. */
        CALCULATING,

        /** The search runs along the route. */
        ACTIVE,

        /**
         * Destination set, but no route could be obtained — the search falls
         * back to the direction of travel. Not an error that empties the
         * list, but the driver should know they're not getting what they
         * configured.
         */
        UNAVAILABLE,
    }

    enum class Phase {
        /** No location yet — neither denied nor failed, just not here yet. */
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
