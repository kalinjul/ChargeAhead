package org.julakali.chargeahead.shared.domain

/**
 * A destination the driver wants to keep, under a name of their choosing.
 *
 * Stores the destination, not the computed route; reopening replans it.
 * [summary] is a snapshot from the moment of saving ("842 km · 3 Stopps").
 */
data class SavedRoute(
    val id: String,
    val name: String,
    val destination: Destination,
    val summary: String? = null,
)
