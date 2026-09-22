package org.julakali.chargeahead.shared.domain

/** A site's live charge points that offer the same connectors at the same power. */
data class LiveConnectorGroup(
    val connectors: List<ConnectorType>,
    val maxPowerKw: Double?,
    val available: Int,
    val occupied: Int,
    val outOfOrder: Int,
    val unknown: Int,
) {
    val total: Int get() = available + occupied + outOfOrder + unknown

    companion object {

        /** Strongest first; points without a known power last. */
        fun of(points: List<ChargePointStatus>): List<LiveConnectorGroup> =
            points
                .groupBy { it.connectors.distinct().sorted() to it.maxPowerKw }
                .map { (key, group) ->
                    LiveConnectorGroup(
                        connectors = key.first,
                        maxPowerKw = key.second,
                        available = group.count { it.state == ChargePointState.AVAILABLE },
                        occupied = group.count { it.state == ChargePointState.OCCUPIED || it.state == ChargePointState.RESERVED },
                        outOfOrder = group.count { it.state == ChargePointState.OUT_OF_ORDER || it.state == ChargePointState.BLOCKED },
                        unknown = group.count { it.state == ChargePointState.UNKNOWN },
                    )
                }
                .sortedByDescending { it.maxPowerKw ?: -1.0 }
    }
}
