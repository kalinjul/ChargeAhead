package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.core.ChargeNowRanker
import org.julakali.chargeahead.shared.core.MIN_DC_POWER_KW
import org.julakali.chargeahead.shared.core.ChargeNowResult
import org.julakali.chargeahead.shared.core.TripPlanResult
import org.julakali.chargeahead.shared.core.TripPlanner
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.distanceKmTo
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.ViewportArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** A charger as the map shows it: the site and its strongest DC power. */
data class MapCharger(
    val site: ChargeSite,
    val maxPowerKw: Double,
)

/**
 * The phone's planning flows: trip with charging stops, "charge now", and the
 * map's chargers.
 *
 * Stateless; reads the driver's configuration from [SettingsStore] at call time.
 */
class PlanningFeature(
    private val tripPlanner: TripPlanner,
    private val repository: SiteRepository,
    private val settings: SettingsStore,
) {

    /**
     * Plans [from] → [destination] with the selected vehicle.
     *
     * [socOverridePercent] plans with a charge level that isn't persisted;
     * otherwise the stored manual value applies, and without one,
     * [DEFAULT_ASSUMED_SOC_PERCENT].
     */
    suspend fun planTrip(
        from: LatLon,
        destination: Destination,
        socOverridePercent: Double? = null,
    ): TripPlanResult = withContext(Dispatchers.Default) {
        val vehicle = settings.vehicle.first() ?: return@withContext TripPlanResult.NoVehicle
        val soc = socOverridePercent
            ?: settings.manualSocPercent.first()
            ?: DEFAULT_ASSUMED_SOC_PERCENT
        tripPlanner.plan(
            from = from,
            destination = destination,
            vehicle = vehicle,
            startSocPercent = soc,
            arrivalSocPercent = settings.arrivalSocPercent.first(),
            filters = settings.chargeFilters.first(),
            networks = settings.networks.first(),
        )
    }

    /** The best chargers around [position], honoring — and if need be relaxing — the filters. */
    suspend fun chargeNow(position: LatLon): ChargeNowResult = withContext(Dispatchers.Default) {
        val filters = settings.chargeFilters.first()
        val networks = settings.networks.first()
        // Fetch wider than the distance filter, so relaxing the distance has data.
        val radius = maxOf(filters.maxDistanceKm * RELAX_FETCH_FACTOR, MIN_FETCH_RADIUS_KM)
        val sites = try {
            repository.sitesIn(SectorArea.circle(position, radius), networks.selectedNetworks())
        } catch (failure: Exception) {
            emptyList()
        }
        ChargeNowRanker.rank(
            sites = sites,
            position = position,
            filters = filters,
            networks = networks,
        )
    }

    /**
     * The chargers the map should show for its current viewport.
     *
     * Fetches the visible viewport, renders a padded area from cache. Capped
     * nearest-first.
     */
    suspend fun chargersIn(viewport: BoundingBox): List<MapCharger> {
        val filters = settings.chargeFilters.first()
        val networks = settings.networks.first()

        // Slow mode browses every network, so it fetches unfiltered.
        val fetchNetworks = if (filters.slowMode) emptyList() else networks.selectedNetworks()
        runCatching { repository.sitesIn(ViewportArea(viewport), fetchNetworks) }

        val stored = repository.storedSitesIn(viewport.paddedByViewports(MAP_RENDER_PADDING_VIEWPORTS))

        val centre = LatLon((viewport.south + viewport.north) / 2.0, (viewport.west + viewport.east) / 2.0)

        return stored.mapNotNull { site ->
            if (filters.slowMode) {
                // Slow chargers: strongest connector of any type below the DC floor.
                val power = site.connectors.maxOfOrNull { it.maxPowerKw } ?: return@mapNotNull null
                return@mapNotNull if (power < MIN_DC_POWER_KW) MapCharger(site, power) else null
            }
            if (!networks.allowsSite(site)) return@mapNotNull null
            val power = site.connectors
                .filter { it.type == ConnectorType.CCS2 || it.type == ConnectorType.TESLA_NACS }
                .maxOfOrNull { it.maxPowerKw }
                ?: return@mapNotNull null
            if (power < filters.minPowerKw) return@mapNotNull null
            MapCharger(site, power)
        }
            .sortedBy { centre.distanceKmTo(it.site.position) }
            .take(MAX_MAP_CHARGERS)
    }

    private fun BoundingBox.paddedByViewports(factor: Double): BoundingBox {
        val padLat = (north - south) * factor
        val padLon = (east - west) * factor
        return BoundingBox(
            south = (south - padLat).coerceAtLeast(-90.0),
            west = west - padLon,
            north = (north + padLat).coerceAtMost(90.0),
            east = east + padLon,
        )
    }

    companion object {
        /** Assumed start charge when the driver never entered one. */
        const val DEFAULT_ASSUMED_SOC_PERCENT = 80.0

        const val RELAX_FETCH_FACTOR = 3.0
        const val MIN_FETCH_RADIUS_KM = 15.0

        const val MAX_MAP_CHARGERS = 200

        const val MAP_RENDER_PADDING_VIEWPORTS = 2.0
    }
}
