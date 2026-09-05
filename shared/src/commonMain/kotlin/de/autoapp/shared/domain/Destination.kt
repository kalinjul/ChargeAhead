package de.autoapp.shared.domain

/**
 * The destination, as the driver set it.
 *
 * Carries the name along, not just the coordinates: "48.1407 / 11.5569"
 * means nothing to anyone in the car, "München Hauptbahnhof" does.
 */
data class Destination(
    val name: String,
    val position: LatLon,
)
