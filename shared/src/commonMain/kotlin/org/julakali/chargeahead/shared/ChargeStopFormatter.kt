package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.domain.ChargeNowCandidate
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LiveConnectorGroup
import org.julakali.chargeahead.shared.domain.OperatorShortName
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.Reachability
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.fmt_after_distance
import org.julakali.chargeahead.shared.resources.fmt_arrival_about
import org.julakali.chargeahead.shared.resources.fmt_available_of
import org.julakali.chargeahead.shared.resources.fmt_charge_point
import org.julakali.chargeahead.shared.resources.fmt_charge_points
import org.julakali.chargeahead.shared.resources.fmt_charge_points_unknown
import org.julakali.chargeahead.shared.resources.fmt_charge_to
import org.julakali.chargeahead.shared.resources.fmt_connector_type2
import org.julakali.chargeahead.shared.resources.fmt_connector_unknown
import org.julakali.chargeahead.shared.resources.fmt_marginal
import org.julakali.chargeahead.shared.resources.fmt_out_of_order
import org.julakali.chargeahead.shared.resources.fmt_reachable
import org.julakali.chargeahead.shared.resources.fmt_status_unknown
import org.julakali.chargeahead.shared.resources.fmt_unreachable
import kotlin.math.round

/** Shared display strings, so Android Auto and CarPlay show the same lines. */
object ChargeStopFormatter {

    /** e.g. "8.4 km · CCS 150 kW" or "12 km · CCS 150 kW". */
    fun primaryLine(stop: ChargeStop): String {
        val distancePart = formatDistanceKm(stop.distanceKm)
        val connector = stop.primaryConnector ?: strongestConnector(stop.site.connectors)
        return if (connector != null) {
            "$distancePart · ${connectorTypeLabel(connector.type)} ${formatPowerKw(connector.maxPowerKw)} kW"
        } else {
            distancePart
        }
    }

    /** Arrival SoC if known — otherwise reachability spelled out as text. */
    fun secondaryLine(stop: ChargeStop): String {
        val soc = stop.socOnArrivalPercent
        return if (soc != null) {
            Texts.string(Res.string.fmt_arrival_about, formatWholeNumber(soc))
        } else {
            when (stop.reachability) {
                Reachability.REACHABLE -> Texts.string(Res.string.fmt_reachable)
                Reachability.MARGINAL -> Texts.string(Res.string.fmt_marginal)
                Reachability.UNREACHABLE -> Texts.string(Res.string.fmt_unreachable)
                Reachability.UNKNOWN -> connectorSummary(stop)
            }
        }
    }

    /** Never empty: this line has a fixed slot in both car UIs. */
    private fun connectorSummary(stop: ChargeStop): String =
        chargePointSummary(stop.site) ?: Texts.string(Res.string.fmt_charge_points_unknown)

    private fun strongestConnector(connectors: List<Connector>): Connector? =
        connectors.maxByOrNull { it.maxPowerKw }

    /** e.g. "Hauptstr. 5, 85095 Denkendorf" — or `null` if nothing is known. */
    fun addressLine(stop: ChargeStop): String? = addressLine(stop.site)

    fun addressLine(site: ChargeSite): String? = site.address?.let(::addressLine)

    fun addressLine(address: Address): String? {
        val place = listOfNotNull(address.postalCode, address.town).joinToString(" ")
        return listOfNotNull(address.street, place.takeIf { it.isNotBlank() })
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
    }

    /** The structured address when it says more than the name, otherwise the description chain. */
    fun detailLine(place: Place): String? =
        place.address?.takeIf { it.street != null || it.postalCode != null }?.let(::addressLine)
            ?: place.description.removePrefix("${place.name}, ").takeIf { it != place.name }

    /** e.g. "Uebel und Gefährlich, Feldstraße 66, 20359 Hamburg". */
    fun label(place: Place): String = listOfNotNull(place.name, detailLine(place)).joinToString(", ")

    fun label(destination: Destination): String =
        listOfNotNull(destination.name, destination.address).joinToString(", ")

    /**
     * Every connector individually, strongest first — e.g. "CCS 300 kW · 6 Ladepunkte".
     */
    fun connectorLines(stop: ChargeStop): List<String> =
        stop.site.connectors
            .sortedByDescending { it.maxPowerKw }
            .map { connector ->
                val count = connector.count
                val countPart = count?.let { Texts.plural(Res.plurals.fmt_charge_points, it, it) }
                listOfNotNull(
                    "${connectorTypeLabel(connector.type)} ${formatPowerKw(connector.maxPowerKw)} kW",
                    countPart,
                ).joinToString(" · ")
            }

    /**
     * The live charge points, strongest first — e.g. "CCS 300 kW · 2 von 4 frei · 1 außer Betrieb".
     */
    fun liveConnectorLines(groups: List<LiveConnectorGroup>): List<String> =
        groups.map { group ->
            val types = group.connectors.filter { it != ConnectorType.UNKNOWN }.map(::connectorTypeLabel)
            val offer = listOfNotNull(
                types.joinToString(" / ").takeIf { it.isNotEmpty() },
                group.maxPowerKw?.let { "${formatPowerKw(it)} kW" },
            ).joinToString(" ").ifEmpty { Texts.string(Res.string.fmt_charge_point) }
            val known = group.total - group.unknown
            listOfNotNull(
                offer,
                if (known == 0) Texts.string(Res.string.fmt_status_unknown) else Texts.string(Res.string.fmt_available_of, group.available, known),
                group.outOfOrder.takeIf { it > 0 }?.let { Texts.string(Res.string.fmt_out_of_order, it) },
            ).joinToString(" · ")
        }

    /**
     * Where the data came from — e.g. "Bundesnetzagentur · OpenChargeMap".
     */
    fun sourceLine(stop: ChargeStop): String? =
        stop.site.sources
            // One Mobilithek source per operator ("mobilithek:enbw"); the driver sees just the platform.
            .map { it.substringBefore(':') }
            .distinct()
            .sortedBy { SOURCE_LABELS.keys.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .mapNotNull { SOURCE_LABELS[it] }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }

    private val SOURCE_LABELS = mapOf(
        "mobilithek" to "Mobilithek",
        "bnetza" to "Bundesnetzagentur",
        "ocm" to "OpenChargeMap",
    )

    // --- Car rows: planned trip stops ---

    /** e.g. "1. EnBW" — the operator, because the site name is often just the town. */
    fun plannedStopTitle(ordinal: Int, stop: PlannedStop): String {
        val site = stop.site
        return "$ordinal. ${OperatorShortName.of(site.operator) ?: site.operator ?: site.name}"
    }

    /**
     * e.g. "Hauptstr. 5, 85095 Denkendorf". Falls back to the site name when
     * the title already took the operator; `null` when that would only repeat the title.
     */
    fun plannedStopAddressLine(stop: PlannedStop): String? =
        addressLine(stop.site) ?: stop.site.name.takeIf { stop.site.operator != null }

    /** e.g. "Nach 142 km · 150 kW · 18 → 80% in 25 min". */
    fun plannedStopDetailLine(stop: PlannedStop): String =
        Texts.string(Res.string.fmt_after_distance, formatDistanceKm(stop.kmFromStart)) + " · ${formatPowerKw(stop.maxPowerKw)} kW · " +
            "${formatWholeNumber(stop.arrivalSocPercent)} → ${formatWholeNumber(stop.departureSocPercent)}% " +
            "in ${minutesLabel(stop.chargeMinutes)}"

    /** A bare distance for message texts, same rules as the row lines. */
    fun distanceLabel(distanceKm: Double): String = formatDistanceKm(distanceKm)

    // Labels for lines a platform composes itself.

    /** e.g. "150 kW". */
    fun powerKwLabel(powerKw: Double): String = "${formatPowerKw(powerKw)} kW"

    /** e.g. "25 min". */
    fun minutesLabel(minutes: Double): String = "${formatWholeNumber(minutes)} min"

    /** e.g. "25 min laden bis 69%" — how long, and what it buys. */
    fun chargeToLabel(stop: PlannedStop): String =
        Texts.string(Res.string.fmt_charge_to, minutesLabel(stop.chargeMinutes), formatWholeNumber(stop.departureSocPercent))

    // --- Car rows: charge now ---

    /** e.g. "350 m · EnBW" — meters below one kilometer, the operator when known. */
    fun chargeNowPrimaryLine(candidate: ChargeNowCandidate): String =
        listOfNotNull(
            formatShortDistance(candidate.distanceKm),
            candidate.site.operator,
        ).joinToString(" · ")

    /** e.g. "150 kW · 6 Ladepunkte". */
    fun chargeNowSecondaryLine(candidate: ChargeNowCandidate): String =
        listOfNotNull(
            "${formatPowerKw(candidate.maxPowerKw)} kW",
            chargePointSummary(candidate.site),
        ).joinToString(" · ")

    /**
     * Total installed charge points — `null` when any connector lacks a count.
     */
    private fun chargePointSummary(site: ChargeSite): String? {
        val counts = site.connectors.map { it.count }
        if (counts.isEmpty() || counts.any { it == null }) return null

        val total = counts.filterNotNull().sum()
        return Texts.plural(Res.plurals.fmt_charge_points, total, total)
    }

    // Rounded to 10 m.
    private fun formatShortDistance(distanceKm: Double): String =
        if (distanceKm < 1.0) {
            "${round(distanceKm * 100.0).toLong() * 10} m"
        } else {
            formatDistanceKm(distanceKm)
        }

    /** Display name of a connector type. */
    fun connectorLabel(type: ConnectorType): String = connectorTypeLabel(type)

    private fun connectorTypeLabel(type: ConnectorType): String = when (type) {
        ConnectorType.CCS2 -> "CCS"
        ConnectorType.TYPE2 -> Texts.string(Res.string.fmt_connector_type2)
        ConnectorType.CHADEMO -> "CHAdeMO"
        ConnectorType.TESLA_NACS -> "NACS"
        ConnectorType.SCHUKO -> "Schuko"
        ConnectorType.UNKNOWN -> Texts.string(Res.string.fmt_connector_unknown)
    }

    // One decimal below 10 km, whole numbers above. Rounded first: NumberFormat would round half-even.
    private fun formatDistanceKm(distanceKm: Double): String {
        return if (distanceKm < 10.0) {
            "${formatDecimal(round(distanceKm * 10.0) / 10.0, 1)} km"
        } else {
            "${formatWholeNumber(distanceKm)} km"
        }
    }

    private fun formatPowerKw(powerKw: Double): String = formatWholeNumber(powerKw)

    private fun formatWholeNumber(value: Double): String = round(value).toLong().toString()
}

/** Keeps the address apart from the name. */
fun Place.toDestination(): Destination = Destination(name, position, ChargeStopFormatter.detailLine(this))
