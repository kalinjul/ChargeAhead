package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import org.julakali.chargeahead.shared.domain.Interactor

/** Fetches the live status of one site, unless the last fetch is still fresh. */
class RefreshLiveConnectorsInteractor(
    private val statusRepository: ChargePointStatusRepository,
) : Interactor<RefreshLiveConnectorsInteractor.Params, Unit>() {

    data class Params(val liveStatusId: String)

    override suspend fun doWork(params: Params) {
        statusRepository.refresh(listOf(params.liveStatusId))
    }
}
