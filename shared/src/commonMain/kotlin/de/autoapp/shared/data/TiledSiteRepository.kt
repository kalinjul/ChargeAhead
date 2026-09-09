package de.autoapp.shared.data

import de.autoapp.shared.core.Tiles
import de.autoapp.shared.db.ChargeSiteDatabase
import de.autoapp.shared.db.ChargeSiteEntity
import de.autoapp.shared.db.TileCoverageEntity
import de.autoapp.shared.logWarning
import de.autoapp.shared.domain.Address
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ChargeSiteSource
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The on-disk store (M3) — replacing the in-memory circle from M1.
 *
 * The difference isn't size, it's durability: this store survives the
 * process ending. Someone who drove the same route yesterday still has the
 * list today in a tunnel. That's exactly what it's for — dead zones on the
 * highway are the normal case, not the exception (ARCHITECTURE.md 6).
 *
 * Two rules govern its behavior:
 *
 * 1. **Reads always come from the database**, even while a fetch is pending.
 *    The source only replenishes it.
 * 2. **If replenishing fails, what's already stored is still returned.** A
 *    dead zone must not empty the list; it may only prevent it from getting
 *    better. Only when there's nothing stored at all is the failure
 *    propagated — then an empty list is genuinely unsubstantiated, and the
 *    UI needs to be able to say so.
 */
class TiledSiteRepository(
    private val source: ChargeSiteSource,
    database: ChargeSiteDatabase,
    private val time: TimeProvider,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val prefetchMarginKm: Double = DEFAULT_PREFETCH_MARGIN_KM,
) : SiteRepository {

    private val dao = database.chargeSites()

    // Two concurrent fetches of the same area would be pure waste.
    private val mutex = Mutex()

    override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> = mutex.withLock {
        if (!isCovered(area)) {
            // Deliberately no rethrow: what's already in the database is
            // worth more than an error. Only when that's empty too does the
            // error matter.
            val fetchFailure = runCatching { fetchAndStore(area, networks) }.exceptionOrNull()
            if (fetchFailure != null) {
                logWarning("Source '${source.id}' did not respond", fetchFailure)
                val stored = readStored(area)
                if (stored.isEmpty()) throw fetchFailure
                return@withLock stored
            }
        }
        readStored(area)
    }

    /** Discards coverage, not the sites themselves: the store stays readable. */
    override suspend fun invalidate(): Unit = mutex.withLock {
        dao.clearCoverage(source.id)
    }

    /**
     * Are all tiles in the area fresh?
     *
     * Counted rather than checked one by one: querying 1500 tiles
     * individually would mean 1500 requests to SQLite. The count is enough,
     * because the primary key rules out duplicates — if the count matches the
     * number of tiles the area has, every one of them is present.
     */
    private suspend fun isCovered(area: SearchArea): Boolean {
        val range = Tiles.rangeOf(area.boundingBox)
        val fresh = dao.freshTileCount(
            sourceId = source.id,
            networkKey = "*",
            minTileLat = range.minTileLat.toLong(),
            maxTileLat = range.maxTileLat.toLong(),
            minTileLon = range.minTileLon.toLong(),
            maxTileLon = range.maxTileLon.toLong(),
            notOlderThanMillis = time.nowMillis() - ttlMillis,
        )

        return fresh >= range.count.toLong()
    }

    private suspend fun fetchAndStore(area: SearchArea, networks: List<Network>) {
        // The shape decides what "a bit bigger" looks like — a sector grows
        // into a full circle, a route buffer doesn't grow at all. What gets
        // recorded is exactly the area that was actually fetched.
        val fetchArea = area.prefetchArea(prefetchMarginKm)
        val sites = source.query(fetchArea, networks)
        val now = time.nowMillis()

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
                    fetchedAtMillis = now,
                )
            },
            tiles = Tiles.covering(fetchArea.boundingBox).map { tile ->
                TileCoverageEntity(
                    sourceId = source.id,
                    networkKey = "*", // bridge: per-network stamping arrives in the next task
                    tileLat = tile.lat.toLong(),
                    tileLon = tile.lon.toLong(),
                    fetchedAtMillis = now,
                )
            },
        )
    }

    private suspend fun readStored(area: SearchArea): List<ChargeSite> {
        val box = area.boundingBox
        return dao.sitesInBox(
            south = box.south,
            north = box.north,
            west = box.west,
            east = box.east,
        ).map(ChargeSiteEntity::toDomain)
    }

    companion object {
        /** OpenChargeMap changes slowly — three days, see ARCHITECTURE.md 6. */
        const val DEFAULT_TTL_MILLIS = 3L * 24 * 60 * 60 * 1000

        /** 25 km is roughly a quarter hour of driving at highway speed. */
        const val DEFAULT_PREFETCH_MARGIN_KM = 25.0
    }
}

/**
 * Connectors as a `type:kW:count` list, separated by semicolons. An empty
 * count means unknown — sources often don't report it.
 */
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
                // Unknown names become UNKNOWN instead of throwing: otherwise
                // an older app version would lose the entire store on
                // rollback, just because a newer enum value is stored in it.
                type = ConnectorType.entries.firstOrNull { it.name == parts[0] } ?: ConnectorType.UNKNOWN,
                maxPowerKw = power,
                count = parts[2].toIntOrNull(),
            )
        }
    }

private fun ChargeSiteEntity.toDomain(): ChargeSite = ChargeSite(
    id = id,
    name = name,
    operator = operator,
    operatorId = operatorId,
    position = LatLon(lat, lon),
    connectors = connectors.decodeConnectors(),
    address = Address(street, postalCode, town).takeIf { !it.isEmpty },
    // Each row from the database carries exactly one source; merging happens
    // above this, in MergingSiteRepository.
    sources = setOf(sourceId),
)
