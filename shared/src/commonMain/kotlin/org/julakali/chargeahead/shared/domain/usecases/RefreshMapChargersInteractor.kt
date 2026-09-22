package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.ViewportArea
import org.julakali.chargeahead.shared.domain.mapFilter
import kotlinx.coroutines.flow.first

/**
 * Refills the store for a viewport from the network, with the driver's
 * current network selection. [MapChargersObserver] picks the new sites up
 * from the store's flow.
 */
class RefreshMapChargersInteractor(
    private val repository: SiteRepository,
    private val settings: SettingsStore,
) : Interactor<RefreshMapChargersInteractor.Params, Unit>() {

    data class Params(val viewport: BoundingBox)

    override suspend fun doWork(params: Params) {
        val filter = settings.mapFilter().first()
        // Slow mode browses every network, so it fetches unfiltered.
        val networks = if (filter.slowMode) emptySet() else filter.networks.selectedKeys()
        repository.load(ViewportArea(params.viewport), networks)
    }
}
