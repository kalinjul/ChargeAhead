package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.mapChargersIn
import org.julakali.chargeahead.shared.domain.mapFilter
import kotlinx.coroutines.flow.first

/**
 * Refills the live availability of the chargers the map shows for a
 * viewport. [MapChargersObserver] picks it up from the status store's flow.
 */
class RefreshChargerAvailabilityInteractor(
    private val repository: SiteRepository,
    private val statusRepository: ChargePointStatusRepository,
    private val settings: SettingsStore,
) : Interactor<RefreshChargerAvailabilityInteractor.Params, Unit>() {

    data class Params(val viewport: BoundingBox)

    override suspend fun doWork(params: Params) {
        val filter = settings.mapFilter().first()
        val chargers = repository.mapChargersIn(params.viewport, filter).first()
        val ids = chargers.mapNotNull { it.site.liveStatusId }
        if (ids.isNotEmpty()) statusRepository.refresh(ids)
    }
}
