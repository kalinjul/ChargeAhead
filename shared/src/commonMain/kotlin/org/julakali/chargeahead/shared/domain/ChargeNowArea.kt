package org.julakali.chargeahead.shared.domain

const val CHARGE_NOW_RELAX_FETCH_FACTOR = 3.0
const val CHARGE_NOW_MIN_FETCH_RADIUS_KM = 15.0

/** Wider than the distance filter, so relaxing the distance has data. */
internal fun chargeNowArea(position: LatLon, filters: ChargeFilters): SearchArea = SectorArea.circle(
    position,
    maxOf(filters.maxDistanceKm * CHARGE_NOW_RELAX_FETCH_FACTOR, CHARGE_NOW_MIN_FETCH_RADIUS_KM),
)
