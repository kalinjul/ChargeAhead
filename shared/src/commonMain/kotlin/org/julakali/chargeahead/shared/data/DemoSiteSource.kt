package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeSiteSource
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.destination

/**
 * Fallback source with no network and no key. The UI must make this state
 * visible (`ChargeStopsState.isDemo`).
 *
 * The sites are positioned relative to the center of the queried area.
 */
class DemoSiteSource : ChargeSiteSource {

    override val id: String = "demo"

    override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> {
        val center = area.origin
        return TEMPLATES.map { template ->
            ChargeSite(
                id = "$id:${template.id}",
                name = template.name,
                operator = template.operator,
                position = center.destination(template.bearingDeg, template.distanceKm),
                connectors = template.connectors,
                sources = setOf(id),
            )
        }
    }

    private data class Template(
        val id: String,
        val name: String,
        val operator: String,
        val bearingDeg: Double,
        val distanceKm: Double,
        val connectors: List<Connector>,
    )

    private companion object {
        /** Realistic names and connectors; bearings lie within the ±35° corridor. */
        val TEMPLATES = listOf(
            Template(
                id = "enbw-jura-west", name = "EnBW Schnellladepark Jura-West", operator = "EnBW",
                bearingDeg = 0.0, distanceKm = 3.4,
                connectors = listOf(
                    Connector(ConnectorType.CCS2, maxPowerKw = 300.0, count = 6),
                    Connector(ConnectorType.TYPE2, maxPowerKw = 22.0, count = 2),
                ),
            ),
            Template(
                id = "ionity-fuerholzen", name = "Ionity Fürholzen", operator = "Ionity",
                bearingDeg = 12.0, distanceKm = 14.1,
                connectors = listOf(Connector(ConnectorType.CCS2, maxPowerKw = 350.0, count = 8)),
            ),
            Template(
                id = "aral-pulse-allershausen", name = "Aral pulse Allershausen", operator = "Aral pulse",
                bearingDeg = 349.0, distanceKm = 27.9,
                connectors = listOf(
                    Connector(ConnectorType.CCS2, maxPowerKw = 150.0, count = 4),
                    Connector(ConnectorType.TYPE2, maxPowerKw = 22.0, count = 2),
                ),
            ),
            Template(
                id = "allego-holledau", name = "Allego Holledau", operator = "Allego",
                bearingDeg = 22.0, distanceKm = 41.8,
                connectors = listOf(
                    Connector(ConnectorType.CCS2, maxPowerKw = 100.0, count = 2),
                    Connector(ConnectorType.CHADEMO, maxPowerKw = 50.0, count = 1),
                ),
            ),
            Template(
                id = "ewego-greding", name = "EWE Go Greding", operator = "EWE Go",
                bearingDeg = 334.0, distanceKm = 62.7,
                connectors = listOf(
                    Connector(ConnectorType.CCS2, maxPowerKw = 50.0, count = 2),
                    Connector(ConnectorType.CHADEMO, maxPowerKw = 50.0, count = 1),
                    Connector(ConnectorType.SCHUKO, maxPowerKw = 3.7, count = 2),
                ),
            ),
            Template(
                id = "enbw-aichstetten", name = "EnBW Autohof Aichstetten", operator = "EnBW",
                bearingDeg = 7.0, distanceKm = 76.8,
                connectors = listOf(Connector(ConnectorType.CCS2, maxPowerKw = 150.0, count = 4)),
            ),
            Template(
                id = "ionity-hilpoltstein", name = "Ionity Hilpoltstein", operator = "Ionity",
                bearingDeg = 341.0, distanceKm = 97.2,
                connectors = listOf(
                    Connector(ConnectorType.CCS2, maxPowerKw = 350.0, count = 6),
                    Connector(ConnectorType.TESLA_NACS, maxPowerKw = 250.0, count = 4),
                ),
            ),
            Template(
                id = "aral-pulse-greding-nord", name = "Aral pulse Greding Nord", operator = "Aral pulse",
                bearingDeg = 18.0, distanceKm = 111.8,
                connectors = listOf(
                    Connector(ConnectorType.CCS2, maxPowerKw = 300.0, count = 4),
                    Connector(ConnectorType.TYPE2, maxPowerKw = 22.0, count = 2),
                ),
            ),
        )
    }
}
