package de.autoapp.shared.domain

// Shared contract — see AGENTS.md. Field names and order are binding,
// because androidApp and iosApp compile directly against them.

data class LatLon(val lat: Double, val lon: Double)

enum class ConnectorType { CCS2, TYPE2, CHADEMO, TESLA_NACS, SCHUKO, UNKNOWN }

/**
 * [UNKNOWN] applies as long as neither a vehicle profile nor a charge level
 * is available — so throughout M1. Guessing the reachability would be worse
 * than leaving it open: the driver would trust a classification that has no
 * calculation behind it. From M2 onward, the real assessment takes its place.
 */
enum class Reachability { REACHABLE, MARGINAL, UNREACHABLE, UNKNOWN }

/**
 * [count] is nullable because the sources often don't state the quantity: on
 * OpenChargeMap it's missing for a good half of connectors. Defaulting it to
 * 1 would be a made-up number, and made-up numbers have no place in a car UI.
 */
data class Connector(val type: ConnectorType, val maxPowerKw: Double, val count: Int?)

/**
 * A site's postal address, to the extent the source knows one.
 *
 * Each field nullable on its own: sources fill in varying amounts, and half
 * an address is worth more than none — "Denkendorf" alone already tells the
 * driver whether they're headed the right way.
 */
data class Address(
    val street: String? = null,
    val postalCode: String? = null,
    val town: String? = null,
) {
    val isEmpty: Boolean get() = street == null && postalCode == null && town == null
}

data class ChargeSite(
    /**
     * Source-qualified: "ocm:12345", "bnetza:1141226". For a merged site,
     * the identifier of the authoritative source — which one that is is
     * decided traceably by [sources].
     */
    val id: String,
    val name: String,
    val operator: String?,
    val operatorId: Long? = null,
    val position: LatLon,
    val connectors: List<Connector>,
    val address: Address? = null,
    /**
     * Which sources this site comes from. More than one means it was
     * merged (ARCHITECTURE.md section 5.5).
     */
    val sources: Set<String> = emptySet(),
)

data class ChargeStop(
    val site: ChargeSite,
    val distanceKm: Double,
    val reachability: Reachability,
    val socOnArrivalPercent: Double?,
    /**
     * The connector shown in the first line: the strongest one *this
     * vehicle* can use.
     *
     * Why this lives here and not in the formatter: which connector is the
     * right one depends on the vehicle profile — that's a decision, not
     * formatting. Both car surfaces only translate (AGENTS.md), so the
     * planner makes the call, since it already knows the profile.
     *
     * `null` as long as no profile is available; the formatter then falls
     * back to picking the strongest connector overall.
     */
    val primaryConnector: Connector? = null,
)
