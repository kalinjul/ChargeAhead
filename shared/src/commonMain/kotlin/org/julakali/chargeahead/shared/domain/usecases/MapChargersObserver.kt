package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargePointStatus
import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import org.julakali.chargeahead.shared.domain.MapCharger
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteAvailability
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.SubjectInteractor
import org.julakali.chargeahead.shared.domain.mapChargersIn
import org.julakali.chargeahead.shared.domain.mapFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The chargers the map shows for a viewport: the stored sites that pass the
 * driver's charge filters and networks, capped nearest-first, with the live
 * availability last fetched for them.
 *
 * Never fetches: [RefreshMapChargersInteractor] and [RefreshChargerAvailabilityInteractor] refill
 * the stores, and the stores' flows bring the new data in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapChargersObserver(
    private val repository: SiteRepository,
    private val statusRepository: ChargePointStatusRepository,
    private val settings: SettingsStore,
) : SubjectInteractor<MapChargersObserver.Params, List<MapCharger>>() {

    /** [viewport] `null` means: zoomed out past the point where markers are useful. */
    data class Params(val viewport: BoundingBox?)

    override fun createObservable(params: Params): Flow<List<MapCharger>> {
        val viewport = params.viewport ?: return flowOf(emptyList())
        return settings.mapFilter().flatMapLatest { filter ->
            combine(
                repository.mapChargersIn(viewport, filter),
                statusRepository.statuses,
            ) { chargers, statuses ->
                chargers.map { it.withAvailability(statuses, filter) }
            }
        }.distinctUntilChanged()
    }

    private fun MapCharger.withAvailability(statuses: Map<String, List<ChargePointStatus>>, filter: MapFilter): MapCharger {
        val points = site.liveStatusId?.let(statuses::get) ?: return this
        return copy(availability = SiteAvailability.of(points, filter.slowMode, filter.minPowerKw))
    }
}
