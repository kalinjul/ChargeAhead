package de.autoapp.shared

import de.autoapp.shared.domain.Address
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ChargeStop
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Reachability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChargeStopFormatterTest {

    private val site = ChargeSite(
        id = "test-site",
        name = "Testladepark",
        operator = "TestNetz",
        position = LatLon(48.0, 11.0),
        connectors = listOf(
            Connector(ConnectorType.CCS2, maxPowerKw = 150.0, count = 4),
            Connector(ConnectorType.TYPE2, maxPowerKw = 22.0, count = 2),
        ),
    )

    @Test
    fun primaryLine_belowTenKilometers_showsOneDecimalPlace() {
        val stop = ChargeStop(site, distanceKm = 8.4, reachability = Reachability.REACHABLE, socOnArrivalPercent = 60.0)
        assertEquals("8,4 km · CCS 150 kW", ChargeStopFormatter.primaryLine(stop))
    }

    @Test
    fun primaryLine_atExactlyTenKilometers_showsWholeNumber() {
        val stop = ChargeStop(site, distanceKm = 10.0, reachability = Reachability.REACHABLE, socOnArrivalPercent = 60.0)
        assertEquals("10 km · CCS 150 kW", ChargeStopFormatter.primaryLine(stop))
    }

    @Test
    fun primaryLine_aboveTenKilometers_showsWholeNumber() {
        val stop = ChargeStop(site, distanceKm = 12.0, reachability = Reachability.REACHABLE, socOnArrivalPercent = 60.0)
        assertEquals("12 km · CCS 150 kW", ChargeStopFormatter.primaryLine(stop))
    }

    @Test
    fun primaryLine_choosesTheStrongestConnector() {
        val siteWithStrongChademo = site.copy(
            connectors = listOf(
                Connector(ConnectorType.CCS2, maxPowerKw = 50.0, count = 2),
                Connector(ConnectorType.CHADEMO, maxPowerKw = 100.0, count = 1),
            ),
        )
        val stop = ChargeStop(siteWithStrongChademo, distanceKm = 5.0, reachability = Reachability.REACHABLE, socOnArrivalPercent = 60.0)
        assertEquals("5,0 km · CHAdeMO 100 kW", ChargeStopFormatter.primaryLine(stop))
    }

    @Test
    fun secondaryLine_withKnownSoc_showsArrivalPercent() {
        val stop = ChargeStop(site, distanceKm = 12.0, reachability = Reachability.REACHABLE, socOnArrivalPercent = 34.0)
        assertEquals("Ankunft ca. 34 %", ChargeStopFormatter.secondaryLine(stop))
    }

    @Test
    fun secondaryLine_withUnknownReachability_countsTheChargePoints() {
        // Without a vehicle profile there is no reachability classification. Rather than
        // claim one, the number that is actually known takes its place.
        val stop = ChargeStop(site, distanceKm = 12.0, reachability = Reachability.UNKNOWN, socOnArrivalPercent = null)

        assertEquals("6 Ladepunkte", ChargeStopFormatter.secondaryLine(stop))
    }

    @Test
    fun secondaryLine_withOneChargePoint_usesSingular() {
        val single = site.copy(connectors = listOf(Connector(ConnectorType.CCS2, maxPowerKw = 50.0, count = 1)))
        val stop = ChargeStop(single, distanceKm = 12.0, reachability = Reachability.UNKNOWN, socOnArrivalPercent = null)

        assertEquals("1 Ladepunkt", ChargeStopFormatter.secondaryLine(stop))
    }

    @Test
    fun secondaryLine_withMissingCount_doesNotCount() {
        // A sum of guessed ones would be worse than "unknown".
        val partiallyCounted = site.copy(
            connectors = listOf(
                Connector(ConnectorType.CCS2, maxPowerKw = 150.0, count = 4),
                Connector(ConnectorType.TYPE2, maxPowerKw = 22.0, count = null),
            ),
        )
        val stop = ChargeStop(partiallyCounted, distanceKm = 12.0, reachability = Reachability.UNKNOWN, socOnArrivalPercent = null)

        assertEquals("Ladepunkte unbekannt", ChargeStopFormatter.secondaryLine(stop))
    }

    @Test
    fun secondaryLine_isNeverEmpty() {
        // This line has a fixed spot in both car UIs; empty would look like an app bug.
        val withoutConnectors = site.copy(connectors = emptyList())
        val stop = ChargeStop(withoutConnectors, distanceKm = 12.0, reachability = Reachability.UNKNOWN, socOnArrivalPercent = null)

        assertEquals("Ladepunkte unbekannt", ChargeStopFormatter.secondaryLine(stop))
    }

    @Test
    fun primaryLine_withoutConnectors_showsOnlyTheDistance() {
        val withoutConnectors = site.copy(connectors = emptyList())
        val stop = ChargeStop(withoutConnectors, distanceKm = 12.0, reachability = Reachability.UNKNOWN, socOnArrivalPercent = null)

        assertEquals("12 km", ChargeStopFormatter.primaryLine(stop))
    }

    @Test
    fun secondaryLine_withoutSoc_showsReachabilityAsText() {
        val reachable = ChargeStop(site, distanceKm = 12.0, reachability = Reachability.REACHABLE, socOnArrivalPercent = null)
        val marginal = ChargeStop(site, distanceKm = 12.0, reachability = Reachability.MARGINAL, socOnArrivalPercent = null)
        val unreachable = ChargeStop(site, distanceKm = 12.0, reachability = Reachability.UNREACHABLE, socOnArrivalPercent = null)

        assertEquals("Erreichbar", ChargeStopFormatter.secondaryLine(reachable))
        assertEquals("Knapp", ChargeStopFormatter.secondaryLine(marginal))
        assertEquals("Nicht erreichbar", ChargeStopFormatter.secondaryLine(unreachable))
    }

    @Test
    fun primaryLine_usesThePlannerChosenConnector() {
        // The planner knows the vehicle, the formatter doesn't. Once the choice
        // is already made, the formatter just renders it.
        val stop = ChargeStop(
            site = site,
            distanceKm = 12.0,
            reachability = Reachability.REACHABLE,
            socOnArrivalPercent = 60.0,
            primaryConnector = Connector(ConnectorType.TYPE2, maxPowerKw = 22.0, count = 2),
        )

        assertEquals("12 km · Typ 2 22 kW", ChargeStopFormatter.primaryLine(stop))
    }

    @Test
    fun primaryLine_withoutPreselection_stillUsesTheStrongest() {
        val stop = ChargeStop(site, distanceKm = 12.0, reachability = Reachability.UNKNOWN, socOnArrivalPercent = null)

        assertEquals("12 km · CCS 150 kW", ChargeStopFormatter.primaryLine(stop))
    }

    // --- Detail view ---

    private fun detailStop(site: ChargeSite) =
        ChargeStop(site, distanceKm = 12.0, reachability = Reachability.UNKNOWN, socOnArrivalPercent = null)

    @Test
    fun addressLine_combinesStreetAndTown() {
        val withAddress = site.copy(
            address = Address(street = "Hauptstr. 5", postalCode = "85095", town = "Denkendorf"),
        )

        assertEquals("Hauptstr. 5, 85095 Denkendorf", ChargeStopFormatter.addressLine(detailStop(withAddress)))
    }

    @Test
    fun addressLine_handlesPartialAddresses() {
        // A half address is worth more than none.
        val townOnly = site.copy(address = Address(town = "Denkendorf"))
        val streetOnly = site.copy(address = Address(street = "Hauptstr. 5"))

        assertEquals("Denkendorf", ChargeStopFormatter.addressLine(detailStop(townOnly)))
        assertEquals("Hauptstr. 5", ChargeStopFormatter.addressLine(detailStop(streetOnly)))
    }

    @Test
    fun addressLine_withoutAddress_isNull() {
        assertNull(ChargeStopFormatter.addressLine(detailStop(site)))
    }

    @Test
    fun addressLine_worksOnABareSite() {
        // The charge-now sheet has candidates, not stops.
        val withAddress = site.copy(
            address = Address(street = "Hauptstr. 5", postalCode = "85095", town = "Denkendorf"),
        )

        assertEquals("Hauptstr. 5, 85095 Denkendorf", ChargeStopFormatter.addressLine(withAddress))
    }

    @Test
    fun connectorLines_showsAllConnectorsStrongestFirst() {
        val lines = ChargeStopFormatter.connectorLines(detailStop(site))

        assertEquals(listOf("CCS 150 kW · 4 Ladepunkte", "Typ 2 22 kW · 2 Ladepunkte"), lines)
    }

    @Test
    fun connectorLines_withoutCount_mentionsOnlyPower() {
        val withoutCount = site.copy(
            connectors = listOf(Connector(ConnectorType.CCS2, maxPowerKw = 300.0, count = null)),
        )

        assertEquals(listOf("CCS 300 kW"), ChargeStopFormatter.connectorLines(detailStop(withoutCount)))
    }

    @Test
    fun sourceLine_mentionsBothSourcesOfficialFirst() {
        val merged = site.copy(sources = setOf("ocm", "bnetza"))

        assertEquals(
            "Bundesnetzagentur · OpenChargeMap",
            ChargeStopFormatter.sourceLine(detailStop(merged)),
        )
    }

    @Test
    fun sourceLine_withoutSourceInfo_isNull() {
        assertNull(ChargeStopFormatter.sourceLine(detailStop(site)))
    }
}
