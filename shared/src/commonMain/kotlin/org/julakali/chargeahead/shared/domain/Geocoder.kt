package org.julakali.chargeahead.shared.domain

/** A found place, as presented to the driver for selection. */
data class Place(
    /** Short form for the list, e.g. "München Hauptbahnhof". */
    val name: String,
    /** Full description to distinguish places with the same name. */
    val description: String,
    val position: LatLon,
    /** Broken-down address where the source knows one. */
    val address: Address? = null,
)

/** Resolves typed-in destinations to coordinates. */
interface Geocoder {
    /**
     * @param near biases toward nearby results. Not a hard filter.
     */
    suspend fun search(query: String, near: LatLon? = null, limit: Int = DEFAULT_LIMIT): List<Place>

    companion object {
        const val DEFAULT_LIMIT = 5
    }
}
