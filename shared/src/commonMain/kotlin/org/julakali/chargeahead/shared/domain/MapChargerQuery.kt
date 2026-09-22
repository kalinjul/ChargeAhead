package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** At most this many chargers are on the map at once. */
const val MAX_MAP_CHARGERS = 200

private const val RENDER_PADDING_VIEWPORTS = 2.0

internal fun SettingsStore.mapFilter(): Flow<MapFilter> =
    combine(chargeFilters, networks, MapFilter::of).distinctUntilChanged()

/** The stored sites the map shows for [viewport]: those in a padded box, capped nearest-first. */
internal fun SiteRepository.mapChargersIn(viewport: BoundingBox, filter: MapFilter): Flow<List<MapCharger>> {
    val renderBox = viewport.paddedByViewports(RENDER_PADDING_VIEWPORTS)
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
            .take(MAX_MAP_CHARGERS)
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
