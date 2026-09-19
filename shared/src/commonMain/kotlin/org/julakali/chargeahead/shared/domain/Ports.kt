package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Ongoing stream of location fixes. */
interface LocationSource {
    val updates: Flow<Fix>

    /**
     * The best fix obtainable right now — the platform's last known one, or a
     * freshly computed one. `null` when none can be had.
     */
    suspend fun currentFix(): Fix? = null
}

/** The charging networks worth offering, largest first. */
fun interface NetworkListSource {
    suspend fun networks(): List<Network>
}

/** The networks the backend has listed, kept so a selection outlives the list it came from. */
interface NetworkRepository {
    /** Every network ever listed; [Network.rank] marks those on the current list. */
    val networks: Flow<List<Network>>

    /** Keeps what is stored when the backend fails or has no list yet. */
    suspend fun refresh()
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

/** Builds the search area from a fix. */
interface RouteProvider {
    fun searchArea(fix: Fix, rangeKm: Double): SearchArea
}

/** Clock as a port, for testability. */
fun interface TimeProvider {
    fun nowMillis(): Long
}

/**
 * The stock of charging sites the list is built from. The app always reads
 * from this stock and replenishes it in the background.
 */
interface SiteRepository {
    /** Empty [networkKeys] means every network. */
    suspend fun load(area: SearchArea, networkKeys: Set<String> = emptySet()): List<ChargeSite>

    /**
     * The stored sites in [box] that pass [filter] — no fetch, no coverage
     * check — and again whenever the store changes. An empty list is valid;
     * [load] refills.
     */
    fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = flowOf(emptyList())

    /** Every stored site in [area], unfiltered, and again whenever the store changes. */
    fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = flowOf(emptyList())

    /**
     * Discards the stock so the next access actually queries.
     */
    suspend fun invalidate() {}
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

/** What the driver has configured. Outlives the process. */
interface SettingsStore {
    /** The selected vehicle. */
    val vehicle: Flow<VehicleProfile?>

    /** The whole garage, selected vehicle included. */
    val vehicles: Flow<List<VehicleProfile>>

    /** Typed in by the driver, or the last level the car reported (see RememberingSoCSource). */
    val manualSocPercent: Flow<Double?>

    /** How full the battery should still be at the destination. */
    val arrivalSocPercent: Flow<Double>

    /** `null` means no destination set. */
    val destination: Flow<Destination?>

    val networks: Flow<NetworkPreferences>

    /** Hard limits for the phone flows (planning, "charge now"). */
    val chargeFilters: Flow<ChargeFilters>

    /** Routes kept under a chosen name, newest first. */
    val savedRoutes: Flow<List<SavedRoute>>

    /** The outcome of the last attempt to read the charge level from the vehicle. */
    val socDiagnostics: Flow<SoCDiagnostics?>

    /** Everything the car hardware last delivered, one point per [CarDataKind]. */
    val carDebugData: Flow<List<CarDataPoint>>

    /** Recently used destinations, newest first. */
    val recentDestinations: Flow<List<Destination>>

    suspend fun setVehicle(profile: VehicleProfile?)

    /** Removes from the garage; if it was the selected vehicle, the first remaining one takes over. */
    suspend fun removeVehicle(displayName: String)

    suspend fun setManualSocPercent(socPercent: Double?)

    suspend fun setArrivalSocPercent(socPercent: Double)

    suspend fun setChargeFilters(filters: ChargeFilters)

    suspend fun saveRoute(route: SavedRoute)
    suspend fun renameSavedRoute(id: String, name: String)
    suspend fun removeSavedRoute(id: String)

    /** Sets the destination and adds it to the history. `null` clears the destination. */
    suspend fun setDestination(destination: Destination?)

    suspend fun setNetworks(preferences: NetworkPreferences)

    suspend fun recordSoCDiagnostics(diagnostics: SoCDiagnostics)

    /** Upserts by [CarDataPoint.kind]. */
    suspend fun recordCarDataPoint(point: CarDataPoint)
}
