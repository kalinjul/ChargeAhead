package org.julakali.chargeahead.shared.domain

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
 * Never fetches: [RefreshMapChargers] and [RefreshChargerAvailability] refill
 * the stores, and the stores' flows bring the new data in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMapChargers(
    private val repository: SiteRepository,
    private val statusRepository: ChargePointStatusRepository,
    private val settings: SettingsStore,
) : SubjectInteractor<ObserveMapChargers.Params, List<MapCharger>>() {

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

    companion object {
        const val MAX_CHARGERS = 200

        const val RENDER_PADDING_VIEWPORTS = 2.0
    }
}

internal fun SettingsStore.mapFilter(): Flow<MapFilter> =
    combine(chargeFilters, networks, MapFilter::of).distinctUntilChanged()

/** The stored sites the map shows for [viewport]: those in a padded box, capped nearest-first. */
internal fun SiteRepository.mapChargersIn(viewport: BoundingBox, filter: MapFilter): Flow<List<MapCharger>> {
    val renderBox = viewport.paddedByViewports(ObserveMapChargers.RENDER_PADDING_VIEWPORTS)
    return storedSitesIn(renderBox, filter).map { sites -> nearest(sites, viewport, filter) }
}

private suspend fun nearest(stored: List<ChargeSite>, viewport: BoundingBox, filter: MapFilter): List<MapCharger> =
    withContext(Dispatchers.Default) {
        val centre = LatLon((viewport.south + viewport.north) / 2.0, (viewport.west + viewport.east) / 2.0)
        stored.mapNotNull { site ->
            val power = (if (filter.slowMode) site.maxPowerKw else site.maxDcPowerKw) ?: return@mapNotNull null
            MapCharger(site, power)
        }
            .sortedBy { centre.distanceKmTo(it.site.position) }
            .take(ObserveMapChargers.MAX_CHARGERS)
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
