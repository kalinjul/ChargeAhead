package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.Flow

/** Ongoing stream of location fixes. */
interface LocationSource {
    val updates: Flow<Fix>

    /**
     * The best fix obtainable right now — the platform's last known one, or a
     * freshly computed one. `null` when none can be had.
     */
    suspend fun currentFix(): Fix?
}

/** A charging-site data source. */
interface ChargeSiteSource {
    val id: String

    /**
     * Queries all sites in the area.
     *
     * Takes the whole [SearchArea], not just its bounding rectangle, so
     * sources with radial search can respond sorted by distance.
     *
     * Empty [networkKeys] means every network.
     *
     * Throws on network or server errors.
     */
    suspend fun query(area: SearchArea, networkKeys: Set<String> = emptySet()): List<ChargeSite>
}

/** The charging networks worth offering, largest first. */
fun interface NetworkListSource {
    suspend fun networks(): List<Network>
}

/** The vehicle models the backend offers as garage presets. */
fun interface VehicleCatalogSource {
    suspend fun presets(): List<VehiclePreset>
}

/**
 * The charge level, as good as this platform can get it.
 *
 * `null` in the stream means "this source currently knows nothing".
 */
interface SoCSource {
    val kind: SoCSourceKind
    val energy: Flow<EnergyState?>
}

/** The clock, replaceable in tests. */
fun interface TimeProvider {
    fun nowMillis(): Long
}
