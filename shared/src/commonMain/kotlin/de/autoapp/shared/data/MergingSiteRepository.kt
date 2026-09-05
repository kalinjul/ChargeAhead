package de.autoapp.shared.data

import de.autoapp.shared.core.SiteMerger
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.logWarning
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Layers several stocks on top of each other and merges them.
 *
 * Each source gets its own [TiledSiteRepository] and thus its own tile
 * coverage — the database tracks coverage per source anyway. This is
 * necessary because the sources reach different distances: the German
 * charging-station register only covers Germany, OpenChargeMap the whole
 * world. Shared coverage would claim territory beyond the border had been
 * checked too.
 *
 * Queried concurrently. **If one source fails, the rest still count** — a
 * failure of the register must not empty the list when OpenChargeMap
 * responds. Only when all of them fail is the error passed through.
 */
class MergingSiteRepository(
    private val repositories: List<SiteRepository>,
    private val maxDistanceMeters: Double = SiteMerger.DEFAULT_MAX_DISTANCE_METERS,
) : SiteRepository {

    init {
        require(repositories.isNotEmpty()) { "Without a source there's nothing to merge" }
    }

    override suspend fun sitesIn(area: SearchArea): List<ChargeSite> = coroutineScope {
        val results = repositories
            .map { repository -> async { runCatching { repository.sitesIn(area) } } }
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

    override suspend fun invalidate() {
        repositories.forEach { it.invalidate() }
    }
}
