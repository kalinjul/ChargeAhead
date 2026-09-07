package de.autoapp.shared.domain

/**
 * A destination the driver wants to keep, under a name of their choosing.
 *
 * Deliberately stores the destination, not the computed route: roads, traffic
 * and charging stops change, so reopening a saved route replans it. The
 * [summary] is a snapshot from the moment of saving ("842 km · 3 Stopps") —
 * good enough to recognize the route in a list, not a promise.
 */
data class SavedRoute(
    val id: String,
    val name: String,
    val destination: Destination,
    val summary: String? = null,
)
