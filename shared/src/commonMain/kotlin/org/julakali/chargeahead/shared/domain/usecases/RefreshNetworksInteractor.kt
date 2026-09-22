package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.NetworkRepository

class RefreshNetworksInteractor(
    private val repository: NetworkRepository,
) : Interactor<Unit, Unit>() {

    override suspend fun doWork(params: Unit) = repository.refresh()
}
