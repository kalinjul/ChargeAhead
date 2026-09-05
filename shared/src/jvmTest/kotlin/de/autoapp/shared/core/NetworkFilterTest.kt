package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.OperatorKey
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkFilterTest {

    private val origin = LatLon(48.9331, 11.4779)
    private val area = SectorArea.circle(origin, radiusKm = 100.0)

    private fun site(id: String, operator: String?, distanceKm: Double = 20.0) = ChargeSite(
        id = id,
        name = "Ladepark $id",
        operator = operator,
        position = origin.destination(180.0, distanceKm),
        connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 4)),
    )

    private val sites = listOf(
        site("a", "IONITY GmbH", 10.0),
        site("b", "Ionity", 20.0),
        site("c", "EnBW (D)", 30.0),
        site("d", "Mer Germany GmbH", 40.0),
        site("e", null, 50.0),
    )

    @Test
    fun withoutFilter_allRemain() {
        assertEquals(5, ChargeStopPlanner.plan(area, sites, networks = NetworkPreferences()).size)
    }

    @Test
    fun filterEnabledWithoutSelection_hidesNothing() {
        // Interpreting an empty selection as "show nothing" would be the least
        // friendly reading imaginable.
        val planned = ChargeStopPlanner.plan(
            area,
            sites,
            networks = NetworkPreferences(onlyPreferred = true, preferredOperators = emptySet()),
        )

        assertEquals(5, planned.size)
    }

    @Test
    fun selectedNetwork_catchesBothSpellings() {
        // The actual point of normalization: "IONITY GmbH" and "Ionity"
        // are the same operator.
        val onlyIonity = NetworkPreferences(
            onlyPreferred = true,
            preferredOperators = setOfNotNull(OperatorKey.of("Ionity")),
        )

        val planned = ChargeStopPlanner.plan(area, sites, networks = onlyIonity)

        // a and b are Ionity, e has no known operator.
        assertEquals(listOf("a", "b", "e"), planned.map { it.site.id })
    }

    @Test
    fun sitesWithoutOperator_stayIncluded() {
        // The source may simply not know; a station hidden from the list is
        // worse than one that turns out to be on a different network on arrival.
        val onlyEnbw = NetworkPreferences(
            onlyPreferred = true,
            preferredOperators = setOfNotNull(OperatorKey.of("EnBW")),
        )

        assertTrue("e" in ChargeStopPlanner.plan(area, sites, networks = onlyEnbw).map { it.site.id })
    }

    @Test
    fun multipleNetworks_areAllPassedThrough() {
        val twoNetworks = NetworkPreferences(
            onlyPreferred = true,
            preferredOperators = setOfNotNull(OperatorKey.of("Ionity"), OperatorKey.of("EnBW")),
        )

        val planned = ChargeStopPlanner.plan(area, sites, networks = twoNetworks)

        assertEquals(listOf("a", "b", "c", "e"), planned.map { it.site.id })
    }

    @Test
    fun aNetworkWithNoMatches_yieldsOnlyTheUnknownOnes() {
        // The list is allowed to end up empty — it's the driver's explicit
        // choice. The UI just has to explain why.
        val unknownNetwork = NetworkPreferences(
            onlyPreferred = true,
            preferredOperators = setOfNotNull(OperatorKey.of("Gibt es hier nicht")),
        )

        assertEquals(listOf("e"), ChargeStopPlanner.plan(area, sites, networks = unknownNetwork).map { it.site.id })
    }
}
