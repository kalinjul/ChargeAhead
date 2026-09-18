package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.core.ChargeNowRanker
import org.julakali.chargeahead.shared.core.ChargeNowResult
import org.julakali.chargeahead.shared.core.TripPlanResult
import org.julakali.chargeahead.shared.core.TripPlanner
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The phone's planning flows: trip with charging stops and "charge now".
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
            repository.load(SectorArea.circle(position, radius), networks.selectedNetworks())
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

    companion object {
        /** Assumed start charge when the driver never entered one. */
        const val DEFAULT_ASSUMED_SOC_PERCENT = 80.0

        const val RELAX_FETCH_FACTOR = 3.0
        const val MIN_FETCH_RADIUS_KM = 15.0

        const val STATUS_TTL_MILLIS = 60_000L
    }
}
