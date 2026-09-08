package de.autoapp.shared

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
     * e.g. "6 Ladepunkte" (charge points) — but only when the source gives a
     * count for every connector. If even one count is missing, the sum would
     * be a guess, so the line says so instead. In the OCM sample this applied
     * to 54 of 123 sites.
     *
     * Never empty: this line has a fixed slot in both car UIs, and an empty
     * line reads as an app bug.
     */
    private fun connectorSummary(stop: ChargeStop): String {
        val counts = stop.site.connectors.map { it.count }
        if (counts.isEmpty() || counts.any { it == null }) return "Ladepunkte unbekannt"

        val total = counts.filterNotNull().sum()
        return if (total == 1) "1 Ladepunkt" else "$total Ladepunkte"
    }

    private fun strongestConnector(connectors: List<Connector>): Connector? =
        connectors.maxByOrNull { it.maxPowerKw }

    /** e.g. "Hauptstr. 5, 85095 Denkendorf" — or `null` if nothing is known. */
    fun addressLine(stop: ChargeStop): String? = addressLine(stop.site)

    fun addressLine(site: ChargeSite): String? {
        val address = site.address ?: return null
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
