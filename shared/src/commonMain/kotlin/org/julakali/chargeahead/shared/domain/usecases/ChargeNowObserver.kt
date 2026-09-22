package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.ChargeNowRanker
import org.julakali.chargeahead.shared.domain.ChargeNowResult
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MIN_DC_POWER_KW
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.SubjectInteractor
import org.julakali.chargeahead.shared.domain.chargeNowArea
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
 * The best fast chargers around a position among the stored sites, ranked
 * against the driver's filters — and if need be relaxing them.
 *
 * Never fetches: [RefreshChargeNowInteractor] refills the store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChargeNowObserver(
    private val repository: SiteRepository,
    private val settings: SettingsStore,
) : SubjectInteractor<ChargeNowObserver.Params, ChargeNowResult?>() {

    /** [position] `null` means: no location yet, and no result. */
    data class Params(val position: LatLon?)

    override fun createObservable(params: Params): Flow<ChargeNowResult?> {
        val position = params.position ?: return flowOf(null)
        return combine(settings.chargeFilters, settings.networks, ::Pair)
            .distinctUntilChanged()
            .flatMapLatest { (filters, networks) ->
                val area = chargeNowArea(position, filters)
                // Every DC site, so the ranker can relax the power and network filters.
                repository.storedSitesIn(area.boundingBox, EVERY_DC_SITE).map { sites ->
                    withContext(Dispatchers.Default) {
                        ChargeNowRanker.rank(
                            sites = sites.filter { it.position in area },
                            position = position,
                            filters = filters,
                            networks = networks,
                        )
                    }
                }
            }
    }

    companion object {
        private val EVERY_DC_SITE = MapFilter(NetworkPreferences(), minPowerKw = MIN_DC_POWER_KW, slowMode = false)
    }
}
