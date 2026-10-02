package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** How far around the driver "charge now" refills and ranks. */
const val CHARGE_NOW_RADIUS_KM = 15.0

/** How many of the nearest stored sites the ranking looks at; the sheet shows a handful, the car fewer. */
const val CHARGE_NOW_SLICE = 30

internal fun chargeNowArea(position: LatLon): SearchArea = SectorArea.circle(position, CHARGE_NOW_RADIUS_KM)

/**
 * The stored sites "charge now" ranks from, nearest first and capped. AC mode: the slow posts
 * from any network, as on the map. Otherwise two slices: the nearest that pass the filters, and
 * the nearest strong enough from any network, which the ranker relaxes into. One slice of
 * everything would be a city block of 50 kW posts, with nothing to pick.
 */
internal fun SiteRepository.chargeNowSlice(position: LatLon, filters: ChargeFilters, networks: NetworkPreferences): Flow<List<ChargeSite>> {
    val area = chargeNowArea(position)
    val anyNetwork = MapFilter(NetworkPreferences(), minPowerKw = filters.minPowerKw, slowMode = filters.slowMode)
    val slices = if (filters.slowMode) {
        storedSitesNearest(position, area.boundingBox, anyNetwork, CHARGE_NOW_SLICE)
    } else {
        val wanted = MapFilter(networks, minPowerKw = filters.minPowerKw, slowMode = false)
        combine(
            storedSitesNearest(position, area.boundingBox, wanted, CHARGE_NOW_SLICE),
            storedSitesNearest(position, area.boundingBox, anyNetwork, CHARGE_NOW_SLICE),
        ) { matching, any -> (matching + any).distinctBy { it.id } }
    }
    return slices.map { sites -> sites.filter { it.position in area } }
}
