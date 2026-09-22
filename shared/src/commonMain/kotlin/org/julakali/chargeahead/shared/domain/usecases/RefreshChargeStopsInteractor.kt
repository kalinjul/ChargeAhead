package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import kotlinx.coroutines.flow.first

/**
 * Discards the stock and refills [Params.area] from the network.
 * [ChargeStopsObserver] picks the new sites up from the store's flow.
 */
class RefreshChargeStopsInteractor(
    private val repository: SiteRepository,
    private val settings: SettingsStore,
) : Interactor<RefreshChargeStopsInteractor.Params, Unit>() {

    data class Params(val area: SearchArea)

    override suspend fun doWork(params: Params) {
        repository.invalidate()
        repository.load(params.area, settings.networks.first().selectedKeys())
    }
}
