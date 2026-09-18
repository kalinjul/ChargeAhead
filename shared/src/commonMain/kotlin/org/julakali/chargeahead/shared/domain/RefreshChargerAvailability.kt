package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.first

/**
 * Refills the live availability of the chargers the map shows for a
 * viewport. [ObserveMapChargers] picks it up from the status store's flow.
 */
class RefreshChargerAvailability(
    private val repository: SiteRepository,
    private val statusRepository: ChargePointStatusRepository,
    private val settings: SettingsStore,
) : Interactor<RefreshChargerAvailability.Params, Unit>() {

    data class Params(val viewport: BoundingBox)

    override suspend fun doWork(params: Params) {
        val filter = settings.mapFilter().first()
        val chargers = repository.mapChargersIn(params.viewport, filter).first()
        val ids = chargers.mapNotNull { it.site.liveStatusId }
        if (ids.isNotEmpty()) statusRepository.refresh(ids)
    }
}
