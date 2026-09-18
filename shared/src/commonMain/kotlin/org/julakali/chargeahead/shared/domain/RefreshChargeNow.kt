package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.first

/**
 * Refills the store around a position with the driver's current network
 * selection. [ObserveChargeNow] picks the new sites up from the store's flow.
 */
class RefreshChargeNow(
    private val repository: SiteRepository,
    private val settings: SettingsStore,
) : Interactor<RefreshChargeNow.Params, Unit>() {

    data class Params(val position: LatLon)

    override suspend fun doWork(params: Params) {
        val filters = settings.chargeFilters.first()
        val networks = settings.networks.first()
        repository.load(chargeNowArea(params.position, filters), networks.selectedNetworks())
    }
}
