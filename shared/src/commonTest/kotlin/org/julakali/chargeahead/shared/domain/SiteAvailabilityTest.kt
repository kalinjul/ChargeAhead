package org.julakali.chargeahead.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SiteAvailabilityTest {

    private fun point(state: ChargePointState, vararg connectors: ConnectorType) =
        ChargePointStatus(state = state, connectors = connectors.toList().ifEmpty { listOf(ConnectorType.CCS2) })

    private fun of(vararg points: ChargePointStatus, slowMode: Boolean = false) =
        SiteAvailability.of(points.toList(), slowMode)

    @Test
    fun countsFreePointsAgainstAllKnownOnes() {
        val availability = of(
            point(ChargePointState.AVAILABLE),
            point(ChargePointState.OCCUPIED),
            point(ChargePointState.RESERVED),
            point(ChargePointState.OUT_OF_ORDER),
            point(ChargePointState.UNKNOWN),
        )

        assertEquals(SiteAvailability.Live(free = 1, total = 4), availability)
    }

    @Test
    fun levelIsGoodFromHalfFree() {
        assertEquals(AvailabilityLevel.GOOD, SiteAvailability.Live(2, 2).level)
        assertEquals(AvailabilityLevel.GOOD, SiteAvailability.Live(1, 2).level)
        assertEquals(AvailabilityLevel.LOW, SiteAvailability.Live(1, 4).level)
        assertEquals(AvailabilityLevel.NONE, SiteAvailability.Live(0, 2).level)
    }

    @Test
    fun onlyBrokenPointsMeanOutOfOrder() {
        val availability = of(point(ChargePointState.OUT_OF_ORDER), point(ChargePointState.BLOCKED))

        assertEquals(SiteAvailability.OutOfOrder, availability)
    }

    @Test
    fun noKnownStateMeansNoAvailability() {
        assertNull(of(point(ChargePointState.UNKNOWN)))
        assertNull(of())
    }

    @Test
    fun acPointsCountOnlyInSlowMode() {
        val ac = point(ChargePointState.AVAILABLE, ConnectorType.TYPE2)
        val dc = point(ChargePointState.OCCUPIED, ConnectorType.CCS2)

        assertEquals(SiteAvailability.Live(free = 0, total = 1), of(ac, dc))
        assertEquals(SiteAvailability.Live(free = 1, total = 2), of(ac, dc, slowMode = true))
    }

    @Test
    fun aPointWithoutConnectorTypesCounts() {
        val untyped = ChargePointStatus(state = ChargePointState.AVAILABLE)

        assertEquals(SiteAvailability.Live(free = 1, total = 1), of(untyped))
    }
}
