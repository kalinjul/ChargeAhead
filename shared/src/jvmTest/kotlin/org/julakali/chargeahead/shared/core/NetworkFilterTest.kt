package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NetworkFilterTest {

    private val origin = LatLon(48.9331, 11.4779)
    private val area = SectorArea.circle(origin, radiusKm = 100.0)

    private fun site(id: String, networkKey: String?, distanceKm: Double = 20.0) = ChargeSite(
        id = id,
        name = "Ladepark $id",
        operator = networkKey,
        position = origin.destination(180.0, distanceKm),
        connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 4)),
        networkKey = networkKey,
    )

    private val sites = listOf(
        site("a", "ionity", 10.0),
        site("b", "ionity", 20.0),
        site("c", "enbw", 30.0),
        site("d", "mer", 40.0),
        site("e", null, 50.0),
    )

    @Test
    fun withoutFilter_allRemain() {
        assertEquals(5, ChargeStopPlanner.plan(area, sites, networks = NetworkPreferences()).size)
    }

    @Test
    fun filterEnabledWithoutSelection_hidesNothing() {
        // An empty selection shows everything.
        val planned = ChargeStopPlanner.plan(
            area,
            sites,
            networks = NetworkPreferences(onlyPreferred = true, preferredOperators = emptySet()),
        )

        assertEquals(5, planned.size)
    }

    @Test
    fun selectedNetwork_goesByTheKeyTheBackendAssigned() {
        val onlyIonity = NetworkPreferences(
            onlyPreferred = true,
            preferredOperators = setOf("ionity"),
        )

        val planned = ChargeStopPlanner.plan(area, sites, networks = onlyIonity)

        // e has no network → hidden.
        assertEquals(listOf("a", "b"), planned.map { it.site.id })
    }

    @Test
    fun sitesWithoutOperator_areHiddenWhenFilterIsActive() {
        // Unrecognised sites are hidden when a filter is active.
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

        assertEquals(listOf("a", "b", "c"), planned.map { it.site.id })
    }

    @Test
    fun aNetworkWithNoMatches_yieldsEmptyList() {
        // A network with zero local sites produces an empty list.
        val unknownNetwork = NetworkPreferences(
            onlyPreferred = true,
            preferredOperators = setOf("gibt-es-hier-nicht"),
        )

        assertEquals(emptyList(), ChargeStopPlanner.plan(area, sites, networks = unknownNetwork).map { it.site.id })
    }
}
