package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import org.julakali.chargeahead.shared.domain.LiveConnectorGroup
import org.julakali.chargeahead.shared.domain.SubjectInteractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The live charge points of one site, as last fetched. `null` while the site
 * has no live status or it was not fetched yet; empty when the source knew
 * no points for it.
 */
class LiveConnectorsObserver(
    private val statusRepository: ChargePointStatusRepository,
) : SubjectInteractor<LiveConnectorsObserver.Params, List<LiveConnectorGroup>?>() {

    data class Params(val liveStatusId: String?)

    override fun createObservable(params: Params): Flow<List<LiveConnectorGroup>?> {
        val id = params.liveStatusId ?: return flowOf(null)
        return statusRepository.statuses.map { statuses -> statuses[id]?.let(LiveConnectorGroup::of) }
    }
}
