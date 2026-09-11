package de.autoapp.shared

import de.autoapp.shared.core.ChargeNowCandidate
import de.autoapp.shared.core.PlannedStop
import de.autoapp.shared.domain.Address
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ChargeStop
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Reachability
import kotlin.math.round

/**
 * Formats numbers so that Android Auto and CarPlay are guaranteed to show the
 * same lines (see AGENTS.md). Avoids java.lang.String.format so the code also
 * compiles for Kotlin/Native (iOS).
 */
object ChargeStopFormatter {

    /** e.g. "8.4 km · CCS 150 kW" or "12 km · CCS 150 kW". */
    fun primaryLine(stop: ChargeStop): String {
        val distancePart = formatDistanceKm(stop.distanceKm)
        // The planner already picked the connector this vehicle can use.
        // Without a profile there is no such choice, so power wins instead.
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
            "Ankunft ca. ${formatWholeNumber(soc)} %"
        } else {
            when (stop.reachability) {
                Reachability.REACHABLE -> "Erreichbar"
                Reachability.MARGINAL -> "Knapp"
                Reachability.UNREACHABLE -> "Nicht erreichbar"
                // Without a vehicle profile there is nothing to classify. Rather
                // than assert a classification, fall back to the one number that
                // actually is known: how many charge points are there.
                Reachability.UNKNOWN -> connectorSummary(stop)
            }
        }
    }

    /**
     * Never empty: this line has a fixed slot in both car UIs, and an empty
     * line reads as an app bug.
     */
    private fun connectorSummary(stop: ChargeStop): String =
        chargePointSummary(stop.site) ?: "Ladepunkte unbekannt"

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

    /**
     * Every connector individually, strongest first — e.g. "CCS 300 kW · 6 Ladepunkte".
     *
     * For the detail view. The list line shows only the strongest usable
     * connector; whoever lands here wants to know what else is available.
     */
    fun connectorLines(stop: ChargeStop): List<String> =
        stop.site.connectors
            .sortedByDescending { it.maxPowerKw }
            .map { connector ->
                val count = connector.count
                val countPart = when {
                    count == null -> null
                    count == 1 -> "1 Ladepunkt"
                    else -> "$count Ladepunkte"
                }
                listOfNotNull(
                    "${connectorTypeLabel(connector.type)} ${formatPowerKw(connector.maxPowerKw)} kW",
                    countPart,
                ).joinToString(" · ")
            }

    /**
     * Where the data came from — e.g. "Bundesnetzagentur · OpenChargeMap".
     *
     * Belongs in the detail view because it signals reliability: officially
     * reported data is not the same as community-maintained data.
     */
    fun sourceLine(stop: ChargeStop): String? =
        stop.site.sources
            .sortedBy { SOURCE_LABELS.keys.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .mapNotNull { SOURCE_LABELS[it] }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }

    private val SOURCE_LABELS = mapOf(
        "bnetza" to "Bundesnetzagentur",
        "ocm" to "OpenChargeMap",
        "demo" to "Demodaten",
    )

    // --- Car rows: planned trip stops ---

    /** e.g. "1. Testladepark" — numbered so the car list reads as an itinerary. */
    fun plannedStopTitle(ordinal: Int, stop: PlannedStop): String = "$ordinal. ${stop.site.name}"

    /** e.g. "Nach 142 km · Ankunft ca. 18 %". */
    fun plannedStopPrimaryLine(stop: PlannedStop): String =
        "Nach ${formatDistanceKm(stop.kmFromStart)} · Ankunft ca. ${formatWholeNumber(stop.arrivalSocPercent)} %"

    /** e.g. "150 kW · 6 Ladepunkte · ca. 25 min laden". */
    fun plannedStopSecondaryLine(stop: PlannedStop): String =
        listOfNotNull(
            "${formatPowerKw(stop.maxPowerKw)} kW",
            chargePointSummary(stop.site),
            "ca. ${formatWholeNumber(stop.chargeMinutes)} min laden",
        ).joinToString(" · ")

    /** A bare distance for message texts, same rules as the row lines. */
    fun distanceLabel(distanceKm: Double): String = formatDistanceKm(distanceKm)

    // Labels for lines a platform composes itself (the iOS phone rows) — so
    // both phones show the digits and the comma the car rows already use.

    /** e.g. "150 kW". */
    fun powerKwLabel(powerKw: Double): String = "${formatPowerKw(powerKw)} kW"

    /** e.g. "25 min". */
    fun minutesLabel(minutes: Double): String = "${formatWholeNumber(minutes)} min"

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
     * Total installed charge points — `null` when any connector lacks a count
     * (OCM omits it for about half the sites); the row then simply drops the
     * segment instead of showing a guessed sum.
     */
    private fun chargePointSummary(site: ChargeSite): String? {
        val counts = site.connectors.map { it.count }
        if (counts.isEmpty() || counts.any { it == null }) return null

        val total = counts.filterNotNull().sum()
        return if (total == 1) "1 Ladepunkt" else "$total Ladepunkte"
    }

    // Rounded to 10 m — GPS isn't better, and "347 m" would suggest it is.
    private fun formatShortDistance(distanceKm: Double): String =
        if (distanceKm < 1.0) {
            "${round(distanceKm * 100.0).toLong() * 10} m"
        } else {
            formatDistanceKm(distanceKm)
        }

    /**
     * Display name of a connector type, also used by settings screens. Public
     * so Android and iOS show the same labels as the in-car list — maintaining
     * "CCS" and "Typ 2" twice would drift.
     */
    fun connectorLabel(type: ConnectorType): String = connectorTypeLabel(type)

    private fun connectorTypeLabel(type: ConnectorType): String = when (type) {
        ConnectorType.CCS2 -> "CCS"
        ConnectorType.TYPE2 -> "Typ 2"
        ConnectorType.CHADEMO -> "CHAdeMO"
        ConnectorType.TESLA_NACS -> "NACS"
        ConnectorType.SCHUKO -> "Schuko"
        ConnectorType.UNKNOWN -> "Unbekannt"
    }

    // One decimal below 10 km (otherwise the driver sees "0 km" right before the
    // exit), whole numbers above — German decimal comma, without JVM formatting.
    private fun formatDistanceKm(distanceKm: Double): String {
        return if (distanceKm < 10.0) {
            val tenths = round(distanceKm * 10.0).toLong()
            val whole = tenths / 10
            val fraction = tenths % 10
            "$whole,$fraction km"
        } else {
            "${formatWholeNumber(distanceKm)} km"
        }
    }

    private fun formatPowerKw(powerKw: Double): String = formatWholeNumber(powerKw)

    private fun formatWholeNumber(value: Double): String = round(value).toLong().toString()
}
