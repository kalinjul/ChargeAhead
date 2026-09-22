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

/** Stable identity of a saved route: the position, not the name. */
fun Destination.routeId(): String = "dest:${position.lat},${position.lon}"

/** This destination as a favourite, named after it. */
fun Destination.toSavedRoute(summary: String? = null): SavedRoute =
    SavedRoute(id = routeId(), name = name, destination = this, summary = summary)
