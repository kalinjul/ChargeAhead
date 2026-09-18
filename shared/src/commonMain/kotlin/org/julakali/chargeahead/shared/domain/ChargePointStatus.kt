package org.julakali.chargeahead.shared.domain

enum class ChargePointState { AVAILABLE, OCCUPIED, RESERVED, OUT_OF_ORDER, BLOCKED, UNKNOWN }

data class ChargePointStatus(
    val state: ChargePointState,
    val maxPowerKw: Double? = null,
    val connectors: List<ConnectorType> = emptyList(),
)

fun interface ChargePointStatusSource {
    /** Keyed by [ChargeSite.liveStatusId]; ids the source doesn't know are missing. */
    suspend fun status(siteIds: List<String>): Map<String, List<ChargePointStatus>>
}
