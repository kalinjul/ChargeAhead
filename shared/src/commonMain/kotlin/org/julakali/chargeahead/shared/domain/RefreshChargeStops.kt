package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.first

/**
 * Discards the stock and refills [Params.area] from the network.
 * [ObserveChargeStops] picks the new sites up from the store's flow.
 */
class RefreshChargeStops(
    private val repository: SiteRepository,
    private val settings: SettingsStore,
) : Interactor<RefreshChargeStops.Params, Unit>() {

    data class Params(val area: SearchArea)

    override suspend fun doWork(params: Params) {
        repository.invalidate()
        repository.load(params.area, settings.networks.first().selectedKeys())
    }
}
