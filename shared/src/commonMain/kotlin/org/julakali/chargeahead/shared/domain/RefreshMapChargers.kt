package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Refills the store for a viewport from the network, with the driver's
 * current network selection. [ObserveMapChargers] picks the new sites up
 * from the store's flow.
 */
class RefreshMapChargers(
    private val repository: SiteRepository,
    private val settings: SettingsStore,
) : Interactor<RefreshMapChargers.Params, Unit>() {

    data class Params(val viewport: BoundingBox)

    override suspend fun doWork(params: Params) {
        val filter = combine(settings.chargeFilters, settings.networks, MapFilter::of).first()
        // Slow mode browses every network, so it fetches unfiltered.
        val networks = if (filter.slowMode) emptyList() else filter.networks.selectedNetworks()
        repository.load(ViewportArea(params.viewport), networks)
    }
}
