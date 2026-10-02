package org.julakali.chargeahead.shared.domain

/** How far around the driver "charge now" refills and ranks. */
const val CHARGE_NOW_RADIUS_KM = 15.0

/** How many of the nearest stored sites the ranking looks at; the sheet shows a handful, the car fewer. */
const val CHARGE_NOW_SLICE = 30

internal fun chargeNowArea(position: LatLon): SearchArea = SectorArea.circle(position, CHARGE_NOW_RADIUS_KM)
