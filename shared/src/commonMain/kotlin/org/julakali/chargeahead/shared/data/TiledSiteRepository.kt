package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.core.Coverage
import org.julakali.chargeahead.shared.core.Tiles
import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.db.ChargeSiteEntity
import org.julakali.chargeahead.shared.db.CorridorCoverageEntity
import org.julakali.chargeahead.shared.db.TileCoverageEntity
import org.julakali.chargeahead.shared.logWarning
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeSiteSource
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MIN_DC_POWER_KW
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkCatalog
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.maxDcPowerKw
import org.julakali.chargeahead.shared.domain.maxPowerKw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The on-disk store of charging sites.
 *
 * 1. **Reads always come from the database**; the source only replenishes it.
 * 2. **If replenishing fails, what's already stored is still returned.** Only
 *    when there's nothing stored at all is the failure propagated.
 */
class TiledSiteRepository(
    private val source: ChargeSiteSource,
    database: ChargeSiteDatabase,
    private val time: TimeProvider,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val prefetchMarginKm: Double = DEFAULT_PREFETCH_MARGIN_KM,
) : SiteRepository {

    private val dao = database.chargeSites()

    // De-duplicates identical in-flight fetches.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inFlight = mutableMapOf<String, Deferred<List<ChargeSite>>>()
    private val inFlightGuard = Mutex()

    override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> {
        val key = requestKey(area, networks)
        val deferred = inFlightGuard.withLock {
            inFlight[key] ?: scope.async {
                try {
                    loadSites(area, networks)
                } finally {
                    // Drop it before the result is delivered, so the map only
                    // ever holds live fetches.
                    val self = coroutineContext.job
                    withContext(NonCancellable) {
                        inFlightGuard.withLock { if (inFlight[key] === self) inFlight.remove(key) }
                    }
                }
            }.also { fetch -> inFlight[key] = fetch }
        }
        // await() outside the guard.
        return deferred.await()
    }

    private suspend fun loadSites(area: SearchArea, networks: List<Network>): List<ChargeSite> {
        val keys = if (networks.isEmpty()) listOf(NetworkCatalog.UNFILTERED) else networks.map { it.key }
        val missing = keys.filterNot { isCovered(area, it) }
        if (missing.isNotEmpty()) {
            val toFetch = if (networks.isEmpty()) emptyList()
                          else networks.filter { it.key in missing }
            val fetchFailure = runCatching { fetchAndStore(area, toFetch, missing) }.exceptionOrNull()
            if (fetchFailure != null) {
                logWarning("Source '${source.id}' did not respond", fetchFailure)
                val stored = readStored(area)
                if (stored.isEmpty()) throw fetchFailure
                return stored
            }
        }
        return readStored(area)
    }

    /** Discards coverage, not the sites themselves: the store stays readable. */
    override suspend fun invalidate() {
        dao.clearCoverage(source.id)
    }

    private fun requestKey(area: SearchArea, networks: List<Network>): String {
        val box = area.boundingBox
        val networkKeys = if (networks.isEmpty()) {
            NetworkCatalog.UNFILTERED
        } else {
            networks.map { it.key }.sorted().joinToString(",")
        }
        return "${box.south},${box.west},${box.north},${box.east}|$networkKeys"
    }

    /**
     * Has the area's shape been fetched for the given network key?
     */
    private suspend fun isCovered(area: SearchArea, networkKey: String): Boolean {
        val notOlderThan = time.nowMillis() - ttlMillis
        val box = area.boundingBox
        val range = Tiles.rangeOf(box)
        val freshTiles = dao.freshTilesIn(
            sourceId = source.id,
            networkKey = networkKey,
            minTileLat = range.minTileLat.toLong(),
            maxTileLat = range.maxTileLat.toLong(),
            minTileLon = range.minTileLon.toLong(),
            maxTileLon = range.maxTileLon.toLong(),
            notOlderThanMillis = notOlderThan,
        ).mapTo(HashSet()) { Tiles.Tile(it.tileLat.toInt(), it.tileLon.toInt()) }

        val corridors = if (area is PolylineArea) {
            dao.freshCorridorsIn(
                sourceId = source.id,
                networkKey = networkKey,
                south = box.south,
                west = box.west,
                north = box.north,
                east = box.east,
                notOlderThanMillis = notOlderThan,
            ).map { PolylineArea(it.points.decodePoints(), it.bufferKm) }
        } else {
            emptyList()
        }

        return Coverage.isCovered(area, freshTiles, corridors)
    }

    // networks = the Network objects to query the source with (missing ones only).
    // stampKeys = the network keys to stamp on every covered tile (same as missing keys).
    private suspend fun fetchAndStore(area: SearchArea, networks: List<Network>, stampKeys: List<String>) {
        // Records what the source was asked for: the tiles the shape fully
        // contains, plus the route itself as a corridor. Never its box.
        val fetchArea = area.prefetchArea(prefetchMarginKm)
        val sites = source.query(fetchArea, networks)
        val now = time.nowMillis()
        val tiles = Coverage.tilesToRecord(fetchArea)
        val corridors = if (fetchArea is PolylineArea) {
            val box = fetchArea.boundingBox
            stampKeys.map { key ->
                CorridorCoverageEntity(
                    sourceId = source.id,
                    networkKey = key,
                    points = fetchArea.points.encodePoints(),
                    bufferKm = fetchArea.bufferKm,
                    south = box.south,
                    west = box.west,
                    north = box.north,
                    east = box.east,
                    fetchedAtMillis = now,
                )
            }
        } else {
            emptyList()
        }

        dao.recordFetch(
            sites = sites.map { site ->
                ChargeSiteEntity(
                    id = site.id,
                    sourceId = source.id,
                    name = site.name,
                    operator = site.operator,
                    operatorId = site.operatorId,
                    lat = site.position.lat,
                    lon = site.position.lon,
                    connectors = site.connectors.encode(),
                    street = site.address?.street,
                    postalCode = site.address?.postalCode,
                    town = site.address?.town,
                    liveStatusId = site.liveStatusId,
                    fetchedAtMillis = now,
                    maxPowerKw = site.maxPowerKw,
                    maxDcPowerKw = site.maxDcPowerKw,
                    networkKey = NetworkCatalog.resolve(site),
                )
            },
            tiles = stampKeys.flatMap { key ->
                tiles.map { tile ->
                    TileCoverageEntity(
                        sourceId = source.id,
                        networkKey = key,
                        tileLat = tile.lat.toLong(),
                        tileLon = tile.lon.toLong(),
                        fetchedAtMillis = now,
                    )
                }
            },
            corridors = corridors,
        )
    }

    override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> =
        dao.observeFilteredSitesInBox(
            south = box.south,
            north = box.north,
            west = box.west,
            east = box.east,
            slowMode = filter.slowMode,
            slowBelowKw = MIN_DC_POWER_KW,
            minPowerKw = filter.minPowerKw,
            filterNetworks = filter.networks.isActive,
            networkKeys = filter.networks.preferredOperators.toList(),
        ).map { entities -> entities.map(ChargeSiteEntity::toDomain) }

    private suspend fun readStored(area: SearchArea): List<ChargeSite> {
        val box = area.boundingBox
        return dao.sitesInBox(south = box.south, north = box.north, west = box.west, east = box.east)
            .map(ChargeSiteEntity::toDomain)
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 3L * 24 * 60 * 60 * 1000

        const val DEFAULT_PREFETCH_MARGIN_KM = 25.0
    }
}

/**
 * Connectors as a `type:kW:count` list, separated by semicolons. An empty count means unknown.
 */
// TODO use Room type converters instead (#93)
internal fun List<Connector>.encode(): String =
    joinToString(";") { "${it.type.name}:${it.maxPowerKw}:${it.count ?: ""}" }

internal fun String.decodeConnectors(): List<Connector> =
    if (isEmpty()) {
        emptyList()
    } else {
        split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 3) return@mapNotNull null
            val power = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            Connector(
                // Unknown names become UNKNOWN instead of throwing.
                type = ConnectorType.entries.firstOrNull { it.name == parts[0] } ?: ConnectorType.UNKNOWN,
                maxPowerKw = power,
                count = parts[2].toIntOrNull(),
            )
        }
    }

/** Route points as `lat,lon` pairs, separated by semicolons. */
internal fun List<LatLon>.encodePoints(): String = joinToString(";") { "${it.lat},${it.lon}" }

internal fun String.decodePoints(): List<LatLon> = split(";").map { pair ->
    val (lat, lon) = pair.split(",")
    LatLon(lat.toDouble(), lon.toDouble())
}

private fun ChargeSiteEntity.toDomain(): ChargeSite = ChargeSite(
    id = id,
    name = name,
    operator = operator,
    operatorId = operatorId,
    position = LatLon(lat, lon),
    connectors = connectors.decodeConnectors(),
    address = Address(street, postalCode, town).takeIf { !it.isEmpty },
    sources = setOf(sourceId),
    liveStatusId = liveStatusId,
)
