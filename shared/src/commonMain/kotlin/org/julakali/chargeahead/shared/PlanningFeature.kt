package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.core.ChargeNowRanker
import org.julakali.chargeahead.shared.core.ChargeNowResult
import org.julakali.chargeahead.shared.core.TripPlanResult
import org.julakali.chargeahead.shared.core.TripPlanner
import org.julakali.chargeahead.shared.domain.ChargePointStatus
import org.julakali.chargeahead.shared.domain.ChargePointStatusSource
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MapCharger
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteAvailability
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    /** `null` without a backend: no live data then. */
    private val statusSource: ChargePointStatusSource? = null,
    private val time: TimeProvider = TimeProvider { currentTimeMillis() },
) {

    private val statusCache = mutableMapOf<String, CachedStatus>()
    private val statusCacheLock = Mutex()

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

    /** [chargers] with their live availability, where the backend has any. Failures leave them as they are. */
    suspend fun withAvailability(chargers: List<MapCharger>): List<MapCharger> {
        val source = statusSource ?: return chargers
        val ids = chargers.mapNotNull { it.site.liveStatusId }
        if (ids.isEmpty()) return chargers
        val slowMode = settings.chargeFilters.first().slowMode

        val statuses = statusCacheLock.withLock {
            val now = time.nowMillis()
            statusCache.values.removeAll { now - it.fetchedAtMillis > STATUS_TTL_MILLIS }
            val missing = ids.filter { it !in statusCache }
            if (missing.isNotEmpty()) {
                val fetched = try {
                    source.status(missing)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    emptyMap()
                }
                // Unknown ids are cached too, so they aren't asked for again right away.
                missing.forEach { id -> statusCache[id] = CachedStatus(fetched[id].orEmpty(), now) }
            }
            ids.associateWith { statusCache[it]?.points.orEmpty() }
        }

        return chargers.map { charger ->
            val points = charger.site.liveStatusId?.let { statuses[it] } ?: return@map charger
            charger.copy(availability = SiteAvailability.of(points, slowMode))
        }
    }

    private class CachedStatus(val points: List<ChargePointStatus>, val fetchedAtMillis: Long)

    companion object {
        /** Assumed start charge when the driver never entered one. */
        const val DEFAULT_ASSUMED_SOC_PERCENT = 80.0

        const val RELAX_FETCH_FACTOR = 3.0
        const val MIN_FETCH_RADIUS_KM = 15.0

        const val STATUS_TTL_MILLIS = 60_000L
    }
}
