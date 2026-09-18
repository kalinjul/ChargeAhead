package org.julakali.chargeahead.shared.domain

// Shared contract: androidApp and iosApp compile directly against these names.

data class LatLon(val lat: Double, val lon: Double)

enum class ConnectorType { CCS2, TYPE2, CHADEMO, TESLA_NACS, SCHUKO, UNKNOWN }

/** [UNKNOWN] applies as long as neither a vehicle profile nor a charge level is available. */
enum class Reachability { REACHABLE, MARGINAL, UNREACHABLE, UNKNOWN }

/** [count] is `null` when the source doesn't state the quantity. */
data class Connector(val type: ConnectorType, val maxPowerKw: Double, val count: Int?)

/** A site's postal address, to the extent the source knows one. */
data class Address(
    val street: String? = null,
    val postalCode: String? = null,
    val town: String? = null,
) {
    val isEmpty: Boolean get() = street == null && postalCode == null && town == null
}

data class ChargeSite(
    /** Source-qualified: "ocm:12345", "bnetza:1141226". For a merged site, the leading source's id. */
    val id: String,
    val name: String,
    val operator: String?,
    val operatorId: Long? = null,
    val position: LatLon,
    val connectors: List<Connector>,
    val address: Address? = null,
    /** Which sources this site comes from. More than one means it was merged. */
    val sources: Set<String> = emptySet(),
    /** The id `/v1/charge-point-status` knows this site by; `null` without live status. */
    val liveStatusId: String? = null,
)

data class ChargeStop(
    val site: ChargeSite,
    val distanceKm: Double,
    val reachability: Reachability,
    val socOnArrivalPercent: Double?,
    /** The strongest connector *this vehicle* can use; `null` without a profile. */
    val primaryConnector: Connector? = null,
)
