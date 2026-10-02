package org.julakali.chargeahead.shared.domain


data class ChargeNowCandidate(
    val site: ChargeSite,
    val distanceKm: Double,
    val maxPowerKw: Double,
)

enum class RelaxedFilter { NETWORKS }

data class ChargeNowResult(
    val candidates: List<ChargeNowCandidate>,
    val relaxed: List<RelaxedFilter>,
    /** Everything DC-qualified that missed the top spots, nearest first — the expanded sheet scrolls on. */
    val more: List<ChargeNowCandidate> = emptyList(),
) {
    val isEmpty: Boolean get() = candidates.isEmpty() && more.isEmpty()
}

/**
 * The best charging sites around the current position, nearest first.
 */
object ChargeNowRanker {

    const val MIN_RESULTS = 2
    const val MAX_RESULTS = 3

    fun rank(
        sites: List<ChargeSite>,
        position: LatLon,
        filters: ChargeFilters,
        networks: NetworkPreferences,
    ): ChargeNowResult {
        val candidates = sites.mapNotNull { site ->
            // AC mode browses the slow posts, any connector; otherwise "charge now" means fast
            // charging on CCS2 or NACS, and the driver's minimum is a hard line: a weaker post
            // is never a recommendation.
            val maxPower = if (filters.slowMode) {
                site.maxPowerKw?.takeIf { it < MIN_DC_POWER_KW }
            } else {
                site.maxDcPowerKw?.takeIf { it >= maxOf(MIN_DC_POWER_KW, filters.minPowerKw) }
            } ?: return@mapNotNull null
            ChargeNowCandidate(
                site = site,
                distanceKm = position.distanceKmTo(site.position),
                maxPowerKw = maxPower,
            )
        }

        // Only the networks give way, and only when they were asked for; AC mode ignores them.
        val ladder = if (networks.isActive && !filters.slowMode) {
            listOf(RelaxedFilter.NETWORKS to { c: ChargeNowCandidate -> networks.allowsSite(c.site) })
        } else {
            emptyList()
        }

        for (relaxCount in 0..ladder.size) {
            val enforced = ladder.drop(relaxCount)
            val hits = candidates
                .filter { candidate -> enforced.all { (_, passes) -> passes(candidate) } }
                .sortedBy { it.distanceKm }
                .take(MAX_RESULTS)
            if (hits.size >= MIN_RESULTS || relaxCount == ladder.size) {
                val chosen = hits.map { it.site.id }.toSet()
                return ChargeNowResult(
                    candidates = hits,
                    relaxed = ladder.take(relaxCount).map { it.first },
                    more = candidates.filter { it.site.id !in chosen }.sortedBy { it.distanceKm },
                )
            }
        }
        // Unreachable: the loop always returns on its last iteration.
        return ChargeNowResult(emptyList(), RelaxedFilter.entries.toList())
    }
}
