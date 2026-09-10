package de.autoapp.shared.domain

import kotlinx.coroutines.flow.Flow

/**
 * The ports from ARCHITECTURE.md section 4. They live in `domain` and know
 * nothing else; every implementation lives further out — in `data` or in the
 * respective platform layer.
 */

/** Ongoing stream of location fixes. */
interface LocationSource {
    val updates: Flow<Fix>
}

/** A charging-site data source (OCM in M1, BNetzA from M3 onward). */
interface ChargeSiteSource {
    val id: String

    /**
     * Queries all sites in the area.
     *
     * Takes the whole [SearchArea], not just its bounding rectangle, so
     * sources with radial search can respond sorted by distance. This isn't
     * a nicety: any source with a result cap would otherwise return an
     * arbitrary subset, and the nearest charging sites — exactly the ones
     * being searched for — would be the ones missing from it.
     *
     * Throws on network or server errors; whether the old list stays visible
     * because of that is decided by the calling layer, not the source.
     */
    suspend fun query(area: SearchArea, networks: List<Network> = emptyList()): List<ChargeSite>
}

/** Builds the search area from a fix: corridor in M1, real route from M5 onward. */
interface RouteProvider {
    fun searchArea(fix: Fix, rangeKm: Double): SearchArea
}

/**
 * Access keys for the data sources. The keys themselves don't belong in the
 * repository (see ARCHITECTURE.md section 6); each platform supplies them
 * through its own build mechanism.
 */
interface ApiKeyProvider {
    /** `null` when no key is configured. */
    fun openChargeMapKey(): String?
}

/** Clock as a port, so that expiry times and intervals stay testable. */
fun interface TimeProvider {
    fun nowMillis(): Long
}

/**
 * The stock of charging sites the list is built from.
 *
 * Deliberately placed between the feature and [ChargeSiteSource]: the app
 * always reads from this stock and replenishes it in the background
 * (ARCHITECTURE.md section 6). Dead zones on the highway are the normal
 * case — a list that goes empty in a tunnel is useless. In M1 the stock is
 * held in memory, from M3 onward tile by tile in SQLDelight.
 */
interface SiteRepository {
    suspend fun sitesIn(area: SearchArea, networks: List<Network> = emptyList()): List<ChargeSite>

    /**
     * Returns whatever is already cached in [box] — no fetch, no coverage
     * check. An empty result is valid; use [sitesIn] when you need fresh data.
     */
    suspend fun storedSitesIn(box: BoundingBox): List<ChargeSite> = emptyList()

    /**
     * Discards the stock so the next access actually queries. A no-op by
     * default — a source with no stock has nothing to discard.
     */
    suspend fun invalidate() {}
}

/**
 * The charge level, as good as this platform can get it.
 *
 * Several sources run side by side: manual entry as the baseline, vehicle
 * data as an opportunistic upgrade (ARCHITECTURE.md 1.2). `null` in the
 * stream means "this source currently knows nothing" — for
 * [SoCSourceKind.CAR_HARDWARE] that's the normal case, not the exception.
 */
interface SoCSource {
    val kind: SoCSourceKind
    val energy: Flow<EnergyState?>
}

/** What the driver has configured. Outlives the process. */
interface SettingsStore {
    /** The selected vehicle — what every calculation runs on. */
    val vehicle: Flow<VehicleProfile?>

    /**
     * The whole garage, selected vehicle included. [setVehicle] keeps both in
     * sync: selecting a profile that isn't in the garage yet adds it.
     */
    val vehicles: Flow<List<VehicleProfile>>

    val manualSocPercent: Flow<Double?>

    /** `null` means: no destination set, the corridor ahead in the direction of travel applies. */
    val destination: Flow<Destination?>

    val networks: Flow<NetworkPreferences>

    /** Hard limits for the phone flows (planning, "charge now"). */
    val chargeFilters: Flow<ChargeFilters>

    /** Routes kept under a chosen name. Order is the driver's save order, newest first. */
    val savedRoutes: Flow<List<SavedRoute>>

    /**
     * The outcome of the last attempt to read the charge level from the
     * vehicle — written by the car session, read by the phone UI.
     */
    val socDiagnostics: Flow<SoCDiagnostics?>

    /**
     * Everything the car hardware last delivered, one point per
     * [CarDataKind] — written by the car session, read by the phone's debug
     * view. Survives the drive, so it can be inspected at the desk.
     */
    val carDebugData: Flow<List<CarDataPoint>>

    /**
     * Recently used destinations, newest first.
     *
     * The only way to set a destination in the car: typing at the wheel is
     * out of the question, and the Car App Library deliberately offers no
     * text field.
     */
    val recentDestinations: Flow<List<Destination>>

    suspend fun setVehicle(profile: VehicleProfile?)

    /** Removes from the garage; if it was the selected vehicle, the first remaining one takes over. */
    suspend fun removeVehicle(displayName: String)

    suspend fun setManualSocPercent(socPercent: Double?)

    suspend fun setChargeFilters(filters: ChargeFilters)

    suspend fun saveRoute(route: SavedRoute)
    suspend fun renameSavedRoute(id: String, name: String)
    suspend fun removeSavedRoute(id: String)

    /** Sets the destination and adds it to the history. `null` clears the destination. */
    suspend fun setDestination(destination: Destination?)

    suspend fun setNetworks(preferences: NetworkPreferences)

    suspend fun recordSoCDiagnostics(diagnostics: SoCDiagnostics)

    /** Upserts by [CarDataPoint.kind]: the debug view shows the latest state per data point. */
    suspend fun recordCarDataPoint(point: CarDataPoint)
}
