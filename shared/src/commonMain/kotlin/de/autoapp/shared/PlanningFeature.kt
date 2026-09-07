package de.autoapp.shared

import de.autoapp.shared.core.ChargeNowRanker
import de.autoapp.shared.core.ChargeNowResult
import de.autoapp.shared.core.TripPlanResult
import de.autoapp.shared.core.TripPlanner
import de.autoapp.shared.domain.BoundingBox
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.PriceQuote
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.TariffSource
import de.autoapp.shared.domain.ViewportArea
import kotlinx.coroutines.flow.first

/** A charger as the map shows it: the site, its strongest DC power, and what it costs. */
data class MapCharger(
    val site: ChargeSite,
    val maxPowerKw: Double,
    val quote: PriceQuote,
)

/**
 * The phone's planning flows: trip with charging stops, "charge now", and
 * per-site price quotes. Assembled by [ChargeStopsFeatureFactory] on the same
 * repository and settings as the car feature, so both see the same world.
 *
 * Reads the driver's configuration from [SettingsStore] at call time rather
 * than holding state: planning happens on demand, not continuously — there is
 * nothing to keep hot between calls.
 */
class PlanningFeature(
    private val tripPlanner: TripPlanner,
    private val repository: SiteRepository,
    private val tariffs: TariffSource,
    private val settings: SettingsStore,
) {

    /**
     * Plans [from] → [destination] with the selected vehicle.
     *
     * [socOverridePercent] lets the UI plan with a charge level the driver
     * just typed without persisting it first; otherwise the stored manual
     * value applies, and without one, [DEFAULT_ASSUMED_SOC_PERCENT] — the UI
     * must then say the start level is assumed, not known.
     */
    suspend fun planTrip(
        from: LatLon,
        destination: Destination,
        socOverridePercent: Double? = null,
    ): TripPlanResult {
        val vehicle = settings.vehicle.first() ?: return TripPlanResult.NoVehicle
        val soc = socOverridePercent
            ?: settings.manualSocPercent.first()
            ?: DEFAULT_ASSUMED_SOC_PERCENT
        return tripPlanner.plan(
            from = from,
            destination = destination,
            vehicle = vehicle,
            startSocPercent = soc,
            filters = settings.chargeFilters.first(),
            networks = settings.networks.first(),
            activeTariffIds = settings.activeTariffIds.first(),
        )
    }

    /** The best chargers around [position], honoring — and if need be relaxing — the filters. */
    suspend fun chargeNow(position: LatLon): ChargeNowResult {
        val filters = settings.chargeFilters.first()
        // Fetch wider than the distance filter allows: the relax ladder's
        // last step widens the distance, and it can only widen into data
        // that was actually fetched.
        val radius = maxOf(filters.maxDistanceKm * RELAX_FETCH_FACTOR, MIN_FETCH_RADIUS_KM)
        val sites = try {
            repository.sitesIn(SectorArea.circle(position, radius))
        } catch (failure: Exception) {
            emptyList()
        }
        return ChargeNowRanker.rank(
            sites = sites,
            position = position,
            filters = filters,
            networks = settings.networks.first(),
            tariffs = tariffs,
            activeTariffIds = settings.activeTariffIds.first(),
        )
    }

    /** Price rows for a site's detail view, against the currently active tariffs. */
    suspend fun quote(site: ChargeSite): PriceQuote =
        tariffs.quote(site, settings.activeTariffIds.first())

    /**
     * The chargers the map should show for its current viewport.
     *
     * Filtered by the physical filters (networks, minimum power) — the map
     * shows what exists and qualifies, not what's cheap; price and distance
     * are ranking concerns and stay in [chargeNow]. Capped strongest-first:
     * when a dense city exceeds the cap, the HPC sites are the ones worth
     * keeping visible.
     */
    suspend fun chargersIn(viewport: BoundingBox): List<MapCharger> {
        val filters = settings.chargeFilters.first()
        val networks = settings.networks.first()
        val tariffIds = settings.activeTariffIds.first()

        val sites = try {
            repository.sitesIn(ViewportArea(viewport))
        } catch (failure: Exception) {
            emptyList()
        }

        return sites.mapNotNull { site ->
            if (!networks.allows(site.operator)) return@mapNotNull null
            val power = site.connectors
                .filter { it.type == ConnectorType.CCS2 || it.type == ConnectorType.TESLA_NACS }
                .maxOfOrNull { it.maxPowerKw }
                ?: return@mapNotNull null
            if (power < filters.minPowerKw) return@mapNotNull null
            MapCharger(site, power, tariffs.quote(site, tariffIds))
        }
            .sortedByDescending { it.maxPowerKw }
            .take(MAX_MAP_CHARGERS)
    }

    companion object {
        /** Assumed start charge when the driver never entered one. */
        const val DEFAULT_ASSUMED_SOC_PERCENT = 80.0

        const val RELAX_FETCH_FACTOR = 3.0
        const val MIN_FETCH_RADIUS_KM = 15.0

        /** More markers than this and the map is unreadable anyway. */
        const val MAX_MAP_CHARGERS = 200
    }
}
