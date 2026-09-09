package de.autoapp.shared.core

/**
 * The DC fast-charging floor shared by trip planning and "charge now":
 * below this, a stop is parking, not getting back on the road.
 */
internal const val MIN_DC_POWER_KW = 50.0
