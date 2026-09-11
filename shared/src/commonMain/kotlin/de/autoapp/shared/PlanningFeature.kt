package de.autoapp.shared

import de.autoapp.shared.core.ChargeNowRanker
import de.autoapp.shared.core.MIN_DC_POWER_KW
import de.autoapp.shared.core.ChargeNowResult
import de.autoapp.shared.core.TripPlanResult
import de.autoapp.shared.core.TripPlanner
import de.autoapp.shared.domain.BoundingBox
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.distanceKmTo
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.ViewportArea
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
 * map's chargers. Assembled by [ChargeStopsFeatureFactory] on the same
 * repository and settings as the car feature, so both see the same world.
 *
 * Reads the driver's configuration from [SettingsStore] at call time rather
 * than holding state: planning happens on demand, not continuously — there is
 * nothing to keep hot between calls.
 */
class PlanningFeature(
    private val tripPlanner: TripPlanner,
    private val repository: SiteRepository,
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
    ): TripPlanResult = withContext(Dispatchers.Default) {
        // Off Main, like [chargeNow]: routing and charge-stop optimisation are
        // the heaviest work in the app, and on the main thread they froze the
        // plan sheet's close animation and the planning spinner it kicked off.
        val vehicle = settings.vehicle.first() ?: return@withContext TripPlanResult.NoVehicle
        val soc = socOverridePercent
            ?: settings.manualSocPercent.first()
            ?: DEFAULT_ASSUMED_SOC_PERCENT
        tripPlanner.plan(
            from = from,
            destination = destination,
            vehicle = vehicle,
            startSocPercent = soc,
            filters = settings.chargeFilters.first(),
            networks = settings.networks.first(),
        )
    }

    /** The best chargers around [position], honoring — and if need be relaxing — the filters. */
    suspend fun chargeNow(position: LatLon): ChargeNowResult = withContext(Dispatchers.Default) {
        // The whole sweep runs here, off Main. The ViewModel flips to Loading
        // and the sheet draws its skeleton first; on Main the blocking DB query
        // below held the very frame that would have drawn it, so the sheet only
        // appeared a second later, already full of data.
        val filters = settings.chargeFilters.first()
        val networks = settings.networks.first()
        // Fetch wider than the distance filter allows: the relax ladder's
        // last step widens the distance, and it can only widen into data
        // that was actually fetched.
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
     * Fetch stays on the visible viewport (no bigger downloads). Render reads
     * a padded area from cache — two screens past each edge — so markers don't
     * pop in/out while panning. Filtered by the physical filters (networks,
     * minimum power) and capped nearest-first, so what's in view is never
     * dropped to keep a stronger charger off-screen in the padding.
     */
    suspend fun chargersIn(viewport: BoundingBox): List<MapCharger> {
        val filters = settings.chargeFilters.first()
        val networks = settings.networks.first()

        // Fetch on the strict viewport only — no bigger downloads. Slow mode
        // browses every network, so it fetches unfiltered.
        val fetchNetworks = if (filters.slowMode) emptyList() else networks.selectedNetworks()
        runCatching { repository.sitesIn(ViewportArea(viewport), fetchNetworks) }

        // Render from a padded area so markers stay visible while panning.
        val stored = repository.storedSitesIn(viewport.paddedByViewports(MAP_RENDER_PADDING_VIEWPORTS))

        // Cap keeps the nearest to the visible centre, so a dense city never
        // drops an in-view charger to keep a stronger one off-screen: the far
        // padding markers are the ones that go.
        val centre = LatLon((viewport.south + viewport.north) / 2.0, (viewport.west + viewport.east) / 2.0)

        return stored.mapNotNull { site ->
            if (filters.slowMode) {
                // Browse slow chargers: the strongest connector of any type, as
                // long as it stays under the DC floor. Network and minimum-power
                // filters are deliberately ignored here.
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

        /** More markers than this and the map is unreadable anyway. */
        const val MAX_MAP_CHARGERS = 200

        // render two screens past each edge, from cache, so markers don't vanish when you scroll back
        const val MAP_RENDER_PADDING_VIEWPORTS = 2.0
    }
}
