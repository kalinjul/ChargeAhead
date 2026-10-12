package org.julakali.chargeahead.shared.domain

import kotlinx.serialization.Serializable

// Shared contract: androidApp and iosApp compile directly against these names.

@Serializable
data class LatLon(val lat: Double, val lon: Double)

enum class ConnectorType { CCS2, TYPE2, CHADEMO, TESLA_NACS, SCHUKO, UNKNOWN }

val ConnectorType.isDc: Boolean
    get() = this == ConnectorType.CCS2 || this == ConnectorType.CHADEMO || this == ConnectorType.TESLA_NACS

/** [UNKNOWN] applies as long as neither a vehicle profile nor a charge level is available. */
enum class Reachability { REACHABLE, MARGINAL, UNREACHABLE, UNKNOWN }

/** [count] is `null` when the source doesn't state the quantity. */
@Serializable
data class Connector(val type: ConnectorType = ConnectorType.UNKNOWN, val maxPowerKw: Double, val count: Int?)

/** A site's postal address, to the extent the source knows one. */
@Serializable
data class Address(
    val street: String? = null,
    val postalCode: String? = null,
    val town: String? = null,
) {
    val isEmpty: Boolean get() = street == null && postalCode == null && town == null
}

enum class PriceKind { PER_KWH, PER_MINUTE, SESSION, OTHER }

enum class Weekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

@Serializable
data class PriceComponent(
    val kind: PriceKind,
    /** Gross, in the tariff's currency. */
    val price: Double,
    /** Minutes into the session; `null` means from its start. */
    val fromMinute: Int? = null,
    /** Minutes into the session; `null` means until its end. */
    val toMinute: Int? = null,
    /** ISO-8601 time of day as the operator sends it, e.g. "08:00:00+01:00"; `null` means all day. */
    val startTime: String? = null,
    val endTime: String? = null,
    /** Empty means every day. */
    val days: Set<Weekday> = emptySet(),
)

/** What charging costs without a contract. The [components] add up. */
@Serializable
data class Tariff(
    /** ISO 4217. */
    val currency: String,
    val components: List<PriceComponent>,
    /** The connector types the tariff applies to; empty when the backend doesn't say. */
    val connectors: List<ConnectorType> = emptyList(),
)

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
    /** The backend's key for the site's network; `null` without a known operator. */
    val networkKey: String? = null,
    /** One entry per distinct tariff; empty when the operator publishes none. */
    val adHocTariffs: List<Tariff> = emptyList(),
)

data class ChargeStop(
    val site: ChargeSite,
    val distanceKm: Double,
    val reachability: Reachability,
    val socOnArrivalPercent: Double?,
    /** The strongest connector *this vehicle* can use; `null` without a profile. */
    val primaryConnector: Connector? = null,
)
