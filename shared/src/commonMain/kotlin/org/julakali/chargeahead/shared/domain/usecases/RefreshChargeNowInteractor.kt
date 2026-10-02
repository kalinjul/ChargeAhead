package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.chargeNowArea
import org.julakali.chargeahead.shared.domain.chargeNowSlice
import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import kotlinx.coroutines.flow.first

/**
 * Refills the store around a position with the driver's current network
 * selection. [ChargeNowObserver] picks the new sites up from the store's flow.
 */
class RefreshChargeNowInteractor(
    private val repository: SiteRepository,
    private val statusRepository: ChargePointStatusRepository,
    private val preferences: PreferencesRepository,
) : Interactor<RefreshChargeNowInteractor.Params, Unit>() {

    data class Params(val position: LatLon)

    override suspend fun doWork(params: Params) {
        val filters = preferences.chargeFilters.first()
        val networks = preferences.networks.first()
        // AC mode browses every network, so it fetches unfiltered.
        repository.load(chargeNowArea(params.position), if (filters.slowMode) emptySet() else networks.selectedKeys())
        // Then the live state of what the ranking will look at; the observer picks it up from the status store.
        val ids = repository.chargeNowSlice(params.position, filters, networks).first().mapNotNull { it.liveStatusId }
        if (ids.isNotEmpty()) statusRepository.refresh(ids)
    }
}
