package de.autoapp.shared.domain

/** A found place, as presented to the driver for selection. */
data class Place(
    /** Short form for the list, e.g. "München Hauptbahnhof". */
    val name: String,
    /** Full description to distinguish places with the same name. */
    val description: String,
    val position: LatLon,
    /** Broken-down address where the source knows one — same-name towns need it. */
    val address: Address? = null,
)

/**
 * Resolves typed-in destinations to coordinates.
 *
 * Its own port because route calculation only works between coordinates, and
 * the driver doesn't enter those. Nominatim by default; later the same
 * interface against a self-hosted instance (ARCHITECTURE.md, open item 5).
 */
interface Geocoder {
    /**
     * @param near biases toward nearby results — otherwise "Hauptbahnhof" is
     *   ambiguous. Not a hard filter: a destination may lie arbitrarily far away.
     */
    suspend fun search(query: String, near: LatLon? = null, limit: Int = DEFAULT_LIMIT): List<Place>

    companion object {
        const val DEFAULT_LIMIT = 5
    }
}
