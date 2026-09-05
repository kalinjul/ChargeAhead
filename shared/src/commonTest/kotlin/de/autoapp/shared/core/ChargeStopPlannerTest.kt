package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.EnergyState
import de.autoapp.shared.domain.SoCSourceKind
import de.autoapp.shared.domain.VehicleProfile
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.ROUTE_DETOUR_FACTOR
import de.autoapp.shared.domain.Reachability
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.destination
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChargeStopPlannerTest {

    private val origin = LatLon(48.9331, 11.4779)
    private val southSector = SectorArea(origin, bearingDeg = 180.0, halfAngleDeg = 35.0, radiusKm = 100.0)

    private val vehicle = VehicleProfile(
        displayName = "Testwagen",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 18.0,
        acceptedConnectors = setOf(ConnectorType.CCS2, ConnectorType.TYPE2),
    )

    private fun chargeState(socPercent: Double) =
        EnergyState(socPercent, SoCSourceKind.MANUAL, observedAtMillis = 0L)

    private fun site(id: String, bearingDeg: Double, distanceKm: Double) = ChargeSite(
        id = id,
        name = "Ladepark $id",
        operator = "TestNetz",
        position = origin.destination(bearingDeg, distanceKm),
        connectors = listOf(Connector(ConnectorType.CCS2, maxPowerKw = 150.0, count = 4)),
    )

    @Test
    fun sortsByDistance() {
        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(
                site("fern", 180.0, 60.0),
                site("nah", 180.0, 10.0),
                site("mittel", 180.0, 30.0),
            ),
        )

        assertEquals(listOf("nah", "mittel", "fern"), planned.map { it.site.id })
    }

    @Test
    fun filtersOutWhatLiesOutsideTheSector() {
        // The source returns the enclosing rectangle; anything to the side of
        // or behind the vehicle does not belong in the list.
        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(
                site("voraus", 180.0, 40.0),
                site("hinten", 0.0, 40.0),
                site("quer", 90.0, 40.0),
                site("zuweit", 180.0, 150.0),
            ),
        )

        assertEquals(listOf("voraus"), planned.map { it.site.id })
    }

    @Test
    fun multipliesTheAirlineDistanceByTheDetourFactor() {
        val planned = ChargeStopPlanner.plan(southSector, listOf(site("a", 180.0, 40.0)))

        val expected = 40.0 * ROUTE_DETOUR_FACTOR
        assertTrue(
            abs(planned.single().distanceKm - expected) < 0.1,
            "Expected approx. $expected km, was ${planned.single().distanceKm} km",
        )
    }

    @Test
    fun reachabilityStaysUnknownWithoutAVehicleProfile() {
        val planned = ChargeStopPlanner.plan(southSector, listOf(site("a", 180.0, 40.0)))

        assertEquals(Reachability.UNKNOWN, planned.single().reachability)
        assertEquals(null, planned.single().socOnArrivalPercent)
    }

    @Test
    fun duplicateIdsAppearOnlyOnce() {
        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(site("a", 180.0, 40.0), site("a", 180.0, 40.0)),
        )

        assertEquals(1, planned.size)
    }

    @Test
    fun emptySource_resultsInEmptyList() {
        assertTrue(ChargeStopPlanner.plan(southSector, emptyList()).isEmpty())
    }

    // --- from M2: vehicle profile and charge state ---

    @Test
    fun withVehicleAndChargeState_isClassifiedAndArrivalSocComputed() {
        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(site("nah", 180.0, 20.0)),
            vehicle = vehicle,
            energy = chargeState(80.0),
        )

        val stop = planned.single()
        assertEquals(Reachability.REACHABLE, stop.reachability)
        assertTrue(stop.socOnArrivalPercent!! < 80.0, "Arrival SoC not computed")
    }

    @Test
    fun unreachableSlidesToTheEnd_butIsNotHidden() {
        // ARCHITECTURE.md 5.2: at low charge state the list would otherwise look
        // groundlessly empty and the driver would lose trust in it.
        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(site("fern", 180.0, 90.0), site("nah", 180.0, 10.0)),
            vehicle = vehicle,
            // 15% - 10% reserve = 5% of 77 kWh = 3.85 kWh -> about 21 km
            energy = chargeState(15.0),
        )

        assertEquals(listOf("nah", "fern"), planned.map { it.site.id })
        assertEquals(Reachability.UNREACHABLE, planned.last().reachability)
        assertEquals(2, planned.size, "Unreachable site must not disappear")
    }

    @Test
    fun unreachableAmongThemselves_staySortedByDistance() {
        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(site("sehrfern", 180.0, 90.0), site("fern", 180.0, 60.0)),
            vehicle = vehicle,
            energy = chargeState(12.0),
        )

        assertEquals(listOf("fern", "sehrfern"), planned.map { it.site.id })
    }

    @Test
    fun stationsWithoutAMatchingConnector_areDropped() {
        val chademoOnly = site("chademo", 180.0, 20.0).copy(
            connectors = listOf(Connector(ConnectorType.CHADEMO, maxPowerKw = 50.0, count = 2)),
        )
        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(chademoOnly, site("ccs", 180.0, 30.0)),
            vehicle = vehicle,
            energy = chargeState(80.0),
        )

        assertEquals(listOf("ccs"), planned.map { it.site.id })
    }

    @Test
    fun stationsWithoutKnownConnectors_stayIncluded() {
        // The source may simply not know. A station hidden from the list is
        // worse than one that turns out unsuitable on arrival.
        val noConnectorInfo = site("unbekannt", 180.0, 20.0).copy(connectors = emptyList())

        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(noConnectorInfo),
            vehicle = vehicle,
            energy = chargeState(80.0),
        )

        assertEquals(listOf("unbekannt"), planned.map { it.site.id })
    }

    @Test
    fun withoutChargeState_staysUnknown() {
        // A profile alone is not enough — without a charge state there is nothing to compute.
        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(site("a", 180.0, 20.0)),
            vehicle = vehicle,
            energy = null,
        )

        assertEquals(Reachability.UNKNOWN, planned.single().reachability)
        assertNull(planned.single().socOnArrivalPercent)
    }

    @Test
    fun atEqualPower_theConnectorTheVehicleUsesWins() {
        // Observed at the Köschinger Forst rest stop: CCS and CHAdeMO both at
        // 50 kW, and a CCS vehicle got "CHAdeMO 50 kW" in the headline.
        val both = site("beides", 180.0, 20.0).copy(
            connectors = listOf(
                Connector(ConnectorType.CHADEMO, maxPowerKw = 50.0, count = 2),
                Connector(ConnectorType.CCS2, maxPowerKw = 50.0, count = 2),
            ),
        )

        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(both),
            vehicle = vehicle,
            energy = chargeState(80.0),
        )

        assertEquals(ConnectorType.CCS2, planned.single().primaryConnector?.type)
    }

    @Test
    fun amongUsableConnectors_powerStillWins() {
        val fastAndSlow = site("beides", 180.0, 20.0).copy(
            connectors = listOf(
                Connector(ConnectorType.TYPE2, maxPowerKw = 22.0, count = 2),
                Connector(ConnectorType.CCS2, maxPowerKw = 150.0, count = 4),
            ),
        )

        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(fastAndSlow),
            vehicle = vehicle,
            energy = chargeState(80.0),
        )

        assertEquals(150.0, planned.single().primaryConnector?.maxPowerKw)
    }

    @Test
    fun withoutAProfile_powerAloneDecides() {
        val both = site("beides", 180.0, 20.0).copy(
            connectors = listOf(
                Connector(ConnectorType.CHADEMO, maxPowerKw = 100.0, count = 1),
                Connector(ConnectorType.CCS2, maxPowerKw = 50.0, count = 2),
            ),
        )

        val planned = ChargeStopPlanner.plan(southSector, listOf(both))

        assertEquals(ConnectorType.CHADEMO, planned.single().primaryConnector?.type)
    }

    @Test
    fun noMatchingConnector_theStrongestOneRemains() {
        // Sites whose connectors the source doesn't know deliberately pass through
        // the filter — they must not end up with a blank line.
        val chademoOnly = site("chademo", 180.0, 20.0).copy(
            connectors = listOf(Connector(ConnectorType.CHADEMO, maxPowerKw = 50.0, count = 1)),
        )

        val planned = ChargeStopPlanner.plan(
            southSector,
            listOf(chademoOnly),
            vehicle = vehicle.copy(acceptedConnectors = emptySet()),
            energy = chargeState(80.0),
        )

        assertEquals(ConnectorType.CHADEMO, planned.single().primaryConnector?.type)
    }
}
