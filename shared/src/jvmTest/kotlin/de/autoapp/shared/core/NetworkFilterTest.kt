package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkFilterTest {

    private val origin = LatLon(48.9331, 11.4779)
    private val area = SectorArea.circle(origin, radiusKm = 100.0)

    private fun site(id: String, operator: String?, distanceKm: Double = 20.0, operatorId: Long? = null) = ChargeSite(
        id = id,
        name = "Ladepark $id",
        operator = operator,
        operatorId = operatorId,
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
        // "IONITY GmbH" and "Ionity" both resolve to the "ionity" catalog key.
        val onlyIonity = NetworkPreferences(
            onlyPreferred = true,
            preferredOperators = setOf("ionity"),
        )

        val planned = ChargeStopPlanner.plan(area, sites, networks = onlyIonity)

        // a and b are Ionity. e has no operator → resolves to null → hidden.
        assertEquals(listOf("a", "b"), planned.map { it.site.id })
    }

    @Test
    fun sitesWithoutOperator_areHiddenWhenFilterIsActive() {
        // Previously these slipped through ("unknown → pass"). Now catalog
        // resolution returns null for an unrecognised site, and null is not in
        // any operator set — so it is hidden when a filter is active.
        // That is intentional: the network filter now gives a strict inclusion
        // list, not a "hide the ones I definitely don't want" list.
        val onlyEnbw = NetworkPreferences(
            onlyPreferred = true,
            preferredOperators = setOf("enbw"),
        )

        assertFalse("e" in ChargeStopPlanner.plan(area, sites, networks = onlyEnbw).map { it.site.id })
    }

    @Test
    fun multipleNetworks_areAllPassedThrough() {
        val twoNetworks = NetworkPreferences(
            onlyPreferred = true,
            preferredOperators = setOf("ionity", "enbw"),
        )

        val planned = ChargeStopPlanner.plan(area, sites, networks = twoNetworks)

        // a, b → ionity; c → enbw; d → mer (not selected); e → null (hidden)
        assertEquals(listOf("a", "b", "c"), planned.map { it.site.id })
    }

    @Test
    fun aNetworkWithNoMatches_yieldsEmptyList() {
        // The driver's explicit choice of a network with zero local sites
        // produces an empty list — the UI has to explain why.
        val unknownNetwork = NetworkPreferences(
            onlyPreferred = true,
            preferredOperators = setOf("gibt-es-hier-nicht"),
        )

        assertEquals(emptyList(), ChargeStopPlanner.plan(area, sites, networks = unknownNetwork).map { it.site.id })
    }

    @Test
    fun resolveByOperatorId_takesIdOverName() {
        // A site whose operatorId maps to "enbw" in the catalog is allowed
        // even if its operator string says something unrelated.
        val prefs = NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("enbw"))
        val byId = site("x", operator = "Some Random Name", operatorId = 86L)
        val planned = ChargeStopPlanner.plan(area, listOf(byId), networks = prefs)
        assertEquals(listOf("x"), planned.map { it.site.id })
    }
}
