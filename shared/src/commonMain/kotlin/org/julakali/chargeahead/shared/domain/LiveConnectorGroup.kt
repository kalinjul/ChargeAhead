package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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

/**
 * The live charge points of one site, as last fetched. `null` while the site
 * has no live status or it was not fetched yet; empty when the source knew
 * no points for it.
 */
class ObserveLiveConnectors(
    private val statusRepository: ChargePointStatusRepository,
) : SubjectInteractor<ObserveLiveConnectors.Params, List<LiveConnectorGroup>?>() {

    data class Params(val liveStatusId: String?)

    override fun createObservable(params: Params): Flow<List<LiveConnectorGroup>?> {
        val id = params.liveStatusId ?: return flowOf(null)
        return statusRepository.statuses.map { statuses -> statuses[id]?.let(LiveConnectorGroup::of) }
    }
}

/** Fetches the live status of one site, unless the last fetch is still fresh. */
class RefreshLiveConnectors(
    private val statusRepository: ChargePointStatusRepository,
) : Interactor<RefreshLiveConnectors.Params, Unit>() {

    data class Params(val liveStatusId: String)

    override suspend fun doWork(params: Params) {
        statusRepository.refresh(listOf(params.liveStatusId))
    }
}
