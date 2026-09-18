package org.julakali.chargeahead.shared.domain


data class ChargeNowCandidate(
    val site: ChargeSite,
    val distanceKm: Double,
    val maxPowerKw: Double,
)

enum class RelaxedFilter { MIN_POWER, NETWORKS, MAX_DISTANCE }

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
            val maxPower = site.connectors
                .filter { it.type == ConnectorType.CCS2 || it.type == ConnectorType.TESLA_NACS }
                .maxOfOrNull { it.maxPowerKw }
                ?: return@mapNotNull null
            // A route planner's "charge now" means fast charging; AC posts
            // are for parking, not for getting back on the road.
            if (maxPower < MIN_DC_POWER_KW) return@mapNotNull null
            ChargeNowCandidate(
                site = site,
                distanceKm = position.distanceKmTo(site.position),
                maxPowerKw = maxPower,
            )
        }

        val ladder = listOf(
            RelaxedFilter.MIN_POWER to { c: ChargeNowCandidate -> c.maxPowerKw >= filters.minPowerKw },
            RelaxedFilter.NETWORKS to { c: ChargeNowCandidate -> networks.allowsSite(c.site) },
            RelaxedFilter.MAX_DISTANCE to { c: ChargeNowCandidate -> c.distanceKm <= filters.maxDistanceKm },
        )

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
