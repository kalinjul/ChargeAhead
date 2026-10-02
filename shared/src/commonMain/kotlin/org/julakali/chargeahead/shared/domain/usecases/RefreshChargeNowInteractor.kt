package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.chargeNowArea
import kotlinx.coroutines.flow.first

/**
 * Refills the store around a position with the driver's current network
 * selection. [ChargeNowObserver] picks the new sites up from the store's flow.
 */
class RefreshChargeNowInteractor(
    private val repository: SiteRepository,
    private val preferences: PreferencesRepository,
) : Interactor<RefreshChargeNowInteractor.Params, Unit>() {

    data class Params(val position: LatLon)

    override suspend fun doWork(params: Params) {
        // AC mode browses every network, so it fetches unfiltered.
        val networks = if (preferences.chargeFilters.first().slowMode) emptySet() else preferences.networks.first().selectedKeys()
        repository.load(chargeNowArea(params.position), networks)
    }
}
