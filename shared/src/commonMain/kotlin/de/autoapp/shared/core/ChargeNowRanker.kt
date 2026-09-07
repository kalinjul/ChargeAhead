package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.PriceQuote
import de.autoapp.shared.domain.TariffSource
import de.autoapp.shared.domain.distanceKmTo

/** One "charge now" suggestion: a site, how far away, and what it costs. */
data class ChargeNowCandidate(
    val site: ChargeSite,
    val distanceKm: Double,
    val maxPowerKw: Double,
    val quote: PriceQuote,
)

/** Which filter had to give way so the list didn't starve. Order = relax order. */
enum class RelaxedFilter { MIN_POWER, NETWORKS, MAX_PRICE, MAX_DISTANCE }

data class ChargeNowResult(
    val candidates: List<ChargeNowCandidate>,
    val relaxed: List<RelaxedFilter>,
)

/**
 * The best charging sites around the current position, cheapest first.
 *
 * When the filters starve the list below [MIN_RESULTS], they are relaxed in a
 * fixed order until it recovers: minimum power first (a slower charger beats
 * none), then networks, then price, then — last, because driving across town
 * defeats the point — distance. The result says which filters gave way, so
 * the UI can tell the driver instead of silently ignoring their settings.
 * A user-customizable relax order is on the roadmap.
 *
 * Availability is not part of this: no connected source has live occupancy.
 * Once one exists, "occupied" excludes a site outright and is never relaxed.
 */
object ChargeNowRanker {

    const val MIN_RESULTS = 2
    const val MAX_RESULTS = 3

    fun rank(
        sites: List<ChargeSite>,
        position: LatLon,
        filters: ChargeFilters,
        networks: NetworkPreferences,
        tariffs: TariffSource,
        activeTariffIds: Set<String>,
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
                quote = tariffs.quote(site, activeTariffIds),
            )
        }

        val ladder = listOf(
            RelaxedFilter.MIN_POWER to { c: ChargeNowCandidate -> c.maxPowerKw >= filters.minPowerKw },
            RelaxedFilter.NETWORKS to { c: ChargeNowCandidate -> networks.allows(c.site.operator) },
            RelaxedFilter.MAX_PRICE to { c: ChargeNowCandidate ->
                (c.quote.best?.euroPerKwh ?: 0.0) <= filters.maxPriceEuroPerKwh
            },
            RelaxedFilter.MAX_DISTANCE to { c: ChargeNowCandidate -> c.distanceKm <= filters.maxDistanceKm },
        )

        for (relaxCount in 0..ladder.size) {
            val enforced = ladder.drop(relaxCount)
            val hits = candidates
                .filter { candidate -> enforced.all { (_, passes) -> passes(candidate) } }
                .sortedBy { it.quote.best?.euroPerKwh ?: Double.MAX_VALUE }
                .take(MAX_RESULTS)
            if (hits.size >= MIN_RESULTS || relaxCount == ladder.size) {
                return ChargeNowResult(
                    candidates = hits,
                    relaxed = ladder.take(relaxCount).map { it.first },
                )
            }
        }
        // Unreachable: the loop always returns on its last iteration.
        return ChargeNowResult(emptyList(), RelaxedFilter.entries.toList())
    }

    private const val MIN_DC_POWER_KW = 50.0
}
