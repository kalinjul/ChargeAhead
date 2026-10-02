package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.core.SiteMerger
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.logWarning
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * Layers several stocks on top of each other and merges them.
 *
 * Queried concurrently. **If one source fails, the rest still count**; only
 * when all of them fail is the error passed through.
 */
class MergingSiteRepository(
    private val repositories: List<SiteRepository>,
    private val maxDistanceMeters: Double = SiteMerger.DEFAULT_MAX_DISTANCE_METERS,
    /** Where the stores are read and merged; the collector is usually a ViewModel on main. */
    private val computation: CoroutineDispatcher = Dispatchers.Default,
) : SiteRepository {

    init {
        require(repositories.isNotEmpty()) { "Without a source there's nothing to merge" }
    }

    override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> = coroutineScope {
        val results = repositories
            .map { repository -> async { runCatching { repository.load(area, networkKeys) } } }
            .awaitAll()

        results.forEach { result ->
            result.exceptionOrNull()?.let { logWarning("A source failed", it) }
        }
        val successful = results.mapNotNull { it.getOrNull() }
        if (successful.isEmpty()) {
            throw results.firstNotNullOf { it.exceptionOrNull() }
        }

        SiteMerger.merge(successful.flatten(), maxDistanceMeters)
    }

    override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> =
        combine(
            repositories.map { repository ->
                repository.storedSitesIn(box, filter).catch { failure ->
                    logWarning("A store failed", failure)
                    emit(emptyList())
                }
            },
        ) { stocks -> SiteMerger.merge(stocks.toList().flatten(), maxDistanceMeters) }.flowOn(computation)

    override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> =
        combine(
            repositories.map { repository ->
                repository.storedSitesIn(area).catch { failure ->
                    logWarning("A store failed", failure)
                    emit(emptyList())
                }
            },
        ) { stocks -> SiteMerger.merge(stocks.toList().flatten(), maxDistanceMeters) }.flowOn(computation)

    override suspend fun invalidate() {
        repositories.forEach { it.invalidate() }
    }
}
