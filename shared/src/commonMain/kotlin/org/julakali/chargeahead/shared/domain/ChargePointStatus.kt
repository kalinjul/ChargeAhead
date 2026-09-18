package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.Flow

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

interface ChargePointStatusRepository {
    /** The last fetched statuses, keyed by [ChargeSite.liveStatusId]; an id the source didn't know maps to an empty list. */
    val statuses: Flow<Map<String, List<ChargePointStatus>>>

    /** Fetches those of [ids] that are missing or stale. */
    suspend fun refresh(ids: Collection<String>)
}
