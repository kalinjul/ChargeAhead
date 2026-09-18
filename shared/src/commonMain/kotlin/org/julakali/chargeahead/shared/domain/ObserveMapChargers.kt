package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The chargers the map shows for a viewport: the stored sites that pass the
 * driver's charge filters and networks, capped nearest-first.
 *
 * Never fetches: [RefreshMapChargers] refills the store, and the store's
 * flow brings the new sites in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMapChargers(
    private val repository: SiteRepository,
    private val settings: SettingsStore,
) : SubjectInteractor<ObserveMapChargers.Params, MapChargers>() {

    /** [viewport] `null` means: zoomed out past the point where markers are useful. */
    data class Params(val viewport: BoundingBox?)

    private val filters: Flow<MapFilter> =
        combine(settings.chargeFilters, settings.networks, MapFilter::of).distinctUntilChanged()

    override fun createObservable(params: Params): Flow<MapChargers> {
        val viewport = params.viewport ?: return filters.map { MapChargers(it, emptyList()) }
        val renderBox = viewport.paddedByViewports(RENDER_PADDING_VIEWPORTS)
        return filters.flatMapLatest { filter ->
            repository.storedSitesIn(renderBox, filter).map { sites -> MapChargers(filter, nearest(sites, viewport, filter)) }
        }
    }

    private suspend fun nearest(stored: List<ChargeSite>, viewport: BoundingBox, filter: MapFilter): List<MapCharger> =
        withContext(Dispatchers.Default) {
            val centre = LatLon((viewport.south + viewport.north) / 2.0, (viewport.west + viewport.east) / 2.0)
            stored.mapNotNull { site ->
                val power = (if (filter.slowMode) site.maxPowerKw else site.maxDcPowerKw) ?: return@mapNotNull null
                MapCharger(site, power)
            }
                .sortedBy { centre.distanceKmTo(it.site.position) }
                .take(MAX_CHARGERS)
        }

    private fun BoundingBox.paddedByViewports(factor: Double): BoundingBox {
        val padLat = (north - south) * factor
        val padLon = (east - west) * factor
        return BoundingBox(
            south = (south - padLat).coerceAtLeast(-90.0),
            west = west - padLon,
            north = (north + padLat).coerceAtMost(90.0),
            east = east + padLon,
        )
    }

    companion object {
        const val MAX_CHARGERS = 200

        const val RENDER_PADDING_VIEWPORTS = 2.0
    }
}
