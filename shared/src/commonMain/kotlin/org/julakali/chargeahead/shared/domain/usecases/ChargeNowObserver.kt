package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.CHARGE_NOW_SLICE
import org.julakali.chargeahead.shared.domain.ChargeNowRanker
import org.julakali.chargeahead.shared.domain.ChargeNowResult
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.SubjectInteractor
import org.julakali.chargeahead.shared.domain.chargeNowArea
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The best fast chargers around a position among the stored sites, ranked
 * against the driver's filters — and if need be relaxing them.
 *
 * Never fetches: [RefreshChargeNowInteractor] refills the store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChargeNowObserver(
    private val repository: SiteRepository,
    private val preferences: PreferencesRepository,
    private val dispatchers: AppCoroutineDispatchers,
) : SubjectInteractor<ChargeNowObserver.Params, ChargeNowResult?>() {

    /** [position] `null` means: no location yet, and no result. */
    data class Params(val position: LatLon?)

    override fun createObservable(params: Params): Flow<ChargeNowResult?> {
        val position = params.position ?: return flowOf(null)
        return combine(preferences.chargeFilters, preferences.networks, ::Pair)
            .distinctUntilChanged()
            .flatMapLatest { (filters, networks) ->
                val area = chargeNowArea(position)
                // AC mode: the slow posts from any network, as on the map. Otherwise two slices: the
                // nearest that pass the filters, and the nearest strong enough from any network, which
                // the ranker relaxes into. One slice of everything would be a city block of 50 kW
                // posts, with nothing to pick.
                val anyNetwork = MapFilter(NetworkPreferences(), minPowerKw = filters.minPowerKw, slowMode = filters.slowMode)
                val slices = if (filters.slowMode) {
                    repository.storedSitesNearest(position, area.boundingBox, anyNetwork, CHARGE_NOW_SLICE)
                } else {
                    val wanted = MapFilter(networks, minPowerKw = filters.minPowerKw, slowMode = false)
                    combine(
                        repository.storedSitesNearest(position, area.boundingBox, wanted, CHARGE_NOW_SLICE),
                        repository.storedSitesNearest(position, area.boundingBox, anyNetwork, CHARGE_NOW_SLICE),
                    ) { matching, any -> (matching + any).distinctBy { it.id } }
                }
                slices.map { sites ->
                    ChargeNowRanker.rank(
                        sites = sites.filter { it.position in area },
                        position = position,
                        filters = filters,
                        networks = networks,
                    )
                }
            }
            // Upstream too: the store maps and merges every row per emission, and it emits per tile
            // the refill writes, all while the sheet is animating on main.
            .flowOn(dispatchers.computation)
    }
}
