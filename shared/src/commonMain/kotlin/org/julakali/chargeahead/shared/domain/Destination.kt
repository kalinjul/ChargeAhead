package org.julakali.chargeahead.shared.domain

/** The destination, as the driver set it. */
data class Destination(
    val name: String,
    val position: LatLon,
    /** e.g. "Feldstraße 66, 20359 Hamburg"; null for older destinations. */
    val address: String? = null,
)
