package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.STOP_BUFFER_KM
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.cancellableRunCatching
import org.julakali.chargeahead.shared.logWarning

/**
 * Refills the site store along the planned and the committed trip, then gives
 * their stops what the store has. A stop whose site stays unknown is kept, and
 * marked missing once the store is current along every route.
 */
class RefreshTripSitesInteractor(
    private val trips: TripRepository,
    private val sites: SiteRepository,
    private val preferences: PreferencesRepository,
) : Interactor<Unit, Unit>() {

    override suspend fun doWork(params: Unit) {
        val plans = trips.state.value.plans.filter { it.stops.isNotEmpty() }
        if (plans.isEmpty()) return
        val networkKeys = preferences.networks.first().selectedKeys()
        // load() also returns normally when the fetch failed over a store that still has sites.
        val storeIsCurrent = plans.map { plan ->
            val area = PolylineArea(plan.route.points, STOP_BUFFER_KM)
            cancellableRunCatching {
                sites.load(area, networkKeys)
                sites.covers(area, networkKeys)
            }.getOrElse {
                logWarning("Failed to refresh the sites along the trip", it)
                false
            }
        }.all { it }
        trips.refreshSites(storeIsCurrent)
    }
}
