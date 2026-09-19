package org.julakali.chargeahead.shared.domain

class RefreshNetworks(
    private val repository: NetworkRepository,
) : Interactor<Unit, Unit>() {

    override suspend fun doWork(params: Unit) = repository.refresh()
}
