package org.julakali.chargeahead.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveConnectorGroupTest {

    @Test
    fun pointsWithTheSameOfferAreCountedTogetherStrongestFirst() {
        val points = listOf(
            ChargePointStatus(ChargePointState.AVAILABLE, 50.0, listOf(ConnectorType.CHADEMO, ConnectorType.CCS2)),
            ChargePointStatus(ChargePointState.AVAILABLE, 300.0, listOf(ConnectorType.CCS2)),
            ChargePointStatus(ChargePointState.RESERVED, 300.0, listOf(ConnectorType.CCS2)),
            ChargePointStatus(ChargePointState.BLOCKED, 300.0, listOf(ConnectorType.CCS2)),
            // Same connectors in another order: still the same offer.
            ChargePointStatus(ChargePointState.UNKNOWN, 50.0, listOf(ConnectorType.CCS2, ConnectorType.CHADEMO)),
            ChargePointStatus(ChargePointState.OCCUPIED),
        )

        assertEquals(
            listOf(
                LiveConnectorGroup(listOf(ConnectorType.CCS2), 300.0, available = 1, occupied = 1, outOfOrder = 1, unknown = 0),
                LiveConnectorGroup(listOf(ConnectorType.CCS2, ConnectorType.CHADEMO), 50.0, available = 1, occupied = 0, outOfOrder = 0, unknown = 1),
                LiveConnectorGroup(emptyList(), null, available = 0, occupied = 1, outOfOrder = 0, unknown = 0),
            ),
            LiveConnectorGroup.of(points),
        )
    }
}
