package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

/**
 * The chargers the map shows for a viewport, read from the stored sites and
 * filtered by the driver's charge filters and networks. Capped nearest-first.
 *
 * Emits the stored sites right away, then again once the viewport has been
 * refilled from the network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMapChargers(
    private val repository: SiteRepository,
    private val settings: SettingsStore,
) : SubjectInteractor<ObserveMapChargers.Params, MapChargers>() {

    /** [viewport] `null` means: zoomed out past the point where markers are useful. */
    data class Params(val viewport: BoundingBox?)

    override fun createObservable(params: Params): Flow<MapChargers> =
        combine(settings.chargeFilters, settings.networks, MapFilter::of)
            .distinctUntilChanged()
            .transformLatest { filter ->
                val viewport = params.viewport
                if (viewport == null) {
                    emit(MapChargers(filter, emptyList()))
                    return@transformLatest
                }
                emit(MapChargers(filter, storedChargers(viewport, filter)))
                refill(viewport, filter)
                emit(MapChargers(filter, storedChargers(viewport, filter)))
            }

    private suspend fun refill(viewport: BoundingBox, filter: MapFilter) {
        // Slow mode browses every network, so it fetches unfiltered.
        val networks = if (filter.slowMode) emptyList() else filter.networks.selectedNetworks()
        cancellableRunCatching { repository.sitesIn(ViewportArea(viewport), networks) }
    }

    private suspend fun storedChargers(viewport: BoundingBox, filter: MapFilter): List<MapCharger> {
        val stored = repository.storedSitesIn(viewport.paddedByViewports(RENDER_PADDING_VIEWPORTS))

        return withContext(Dispatchers.Default) {
            val centre = LatLon((viewport.south + viewport.north) / 2.0, (viewport.west + viewport.east) / 2.0)
            stored.mapNotNull { site ->
                if (filter.slowMode) {
                    // Slow chargers: strongest connector of any type below the DC floor.
                    val power = site.connectors.maxOfOrNull { it.maxPowerKw } ?: return@mapNotNull null
                    return@mapNotNull if (power < MIN_DC_POWER_KW) MapCharger(site, power) else null
                }
                if (!filter.networks.allowsSite(site)) return@mapNotNull null
                val power = site.connectors
                    .filter { it.type == ConnectorType.CCS2 || it.type == ConnectorType.TESLA_NACS }
                    .maxOfOrNull { it.maxPowerKw }
                    ?: return@mapNotNull null
                if (power < filter.minPowerKw) return@mapNotNull null
                MapCharger(site, power)
            }
                .sortedBy { centre.distanceKmTo(it.site.position) }
                .take(MAX_CHARGERS)
        }
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
