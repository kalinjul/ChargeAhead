package org.julakali.chargeahead.shared.domain

/**
 * The destination, as the driver set it.
 *
 * Carries the name along, not just the coordinates: "48.1407 / 11.5569"
 * means nothing to anyone in the car, "München Hauptbahnhof" does.
 */
data class Destination(
    val name: String,
    val position: LatLon,
    /** e.g. "Feldstraße 66, 20359 Hamburg"; null for destinations stored before it was kept. */
    val address: String? = null,
)
