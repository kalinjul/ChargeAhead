package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.Route
import de.autoapp.shared.domain.RouteEngine
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.VehicleProfile
import de.autoapp.shared.domain.distanceKmTo
import de.autoapp.shared.domain.distanceKmToSegment
import de.autoapp.shared.domain.projectionOnSegment

/** One planned charging stop along a trip. */
data class PlannedStop(
    val site: ChargeSite,
    /** Position along the route, measured from the start. */
    val kmFromStart: Double,
    val arrivalSocPercent: Double,
    val departureSocPercent: Double,
    val chargeKwh: Double,
    val chargeMinutes: Double,
    /** Driving plus charging time until departure from this stop. */
    val etaMinutesFromStart: Double,
    val maxPowerKw: Double,
)

data class TripPlan(
    val route: Route,
    val destination: Destination,
    val stops: List<PlannedStop>,
    val driveMinutes: Double,
    val chargeMinutes: Double,
    val arrivalSocPercent: Double,
) {
    val totalMinutes: Double get() = driveMinutes + chargeMinutes
}

/** Why no plan came out. The distinction matters to the UI: one asks for patience, the other for a different car. */
sealed interface TripPlanResult {
    data class Planned(val plan: TripPlan) : TripPlanResult

    /** Planning without a vehicle profile would be guesswork — same rule as reachability. */
    data object NoVehicle : TripPlanResult

    /** No road connection, or the route service failed. */
    data object NoRoute : TripPlanResult

    /** A leg has no reachable fast charger — no plan is honest, a pretend plan is not. */
    data class NoChargerInReach(val afterKm: Double) : TripPlanResult
}

/**
 * Plans a trip with charging stops: route from the [RouteEngine], candidate
 * sites along it from the [SiteRepository], stops chosen greedily.
 *
 * Greedy means: drive as far as the battery safely allows, then take the
 * strongest matching charger near the end of that reach. That is
 * prototype-grade — a cost-optimal planner would weigh detour, price, and
 * charging curve against each other globally. Good enough to be honest about
 * times and SoC, which is what the UI shows; not good enough to promise the
 * *optimal* stop set. It errs toward fewer, later, faster stops, which
 * matches how people actually drive long distance.
 *
 * Filters narrow the candidates but never starve a leg: a leg with no charger
 * that passes the filters falls back to any usable fast charger — arriving on
 * a disliked network beats not arriving. The fallback is marked on the stop's
 * quote so the UI can say so.
 */
class TripPlanner(
    private val routeEngine: RouteEngine,
    private val repository: SiteRepository,
) {

    suspend fun plan(
        from: LatLon,
        destination: Destination,
        vehicle: VehicleProfile,
        startSocPercent: Double,
        filters: ChargeFilters = ChargeFilters(),
        networks: NetworkPreferences = NetworkPreferences(),
    ): TripPlanResult {
        val route = try {
            routeEngine.route(from, destination.position)
        } catch (failure: Exception) {
            null
        } ?: return TripPlanResult.NoRoute

        val candidates = candidatesAlong(route, vehicle, networks)

        val cumulativeKm = cumulativeDistances(route.points)
        val totalKm = route.distanceKm
        val minutesPerKm = route.durationMinutes / totalKm

        val stops = mutableListOf<PlannedStop>()
        var socNow = startSocPercent
        var kmNow = 0.0
        var chargeMinutesTotal = 0.0

        while (stops.size < MAX_STOPS) {
            val reachKm = kmNow + RangeCalculator.rangeKm(vehicle, socNow)
            if (totalKm <= reachKm - ARRIVAL_HEADROOM_KM) break

            val stop = pickStop(candidates, kmNow, reachKm, filters, networks)
                ?: return TripPlanResult.NoChargerInReach(afterKm = kmNow)

            val arrivalSoc = RangeCalculator.socOnArrivalPercent(vehicle, socNow, stop.kmFromStart - kmNow)
            val departureSoc = departureSocFor(vehicle, stop.kmFromStart, totalKm, arrivalSoc)
            val chargeKwh = (departureSoc - arrivalSoc) / 100.0 * vehicle.usableBatteryKwh
            val chargeMinutes = chargeMinutes(chargeKwh, stop.maxPowerKw, vehicle)
            chargeMinutesTotal += chargeMinutes

            stops += PlannedStop(
                site = stop.site,
                kmFromStart = stop.kmFromStart,
                arrivalSocPercent = arrivalSoc,
                departureSocPercent = departureSoc,
                chargeKwh = chargeKwh,
                chargeMinutes = chargeMinutes,
                etaMinutesFromStart = stop.kmFromStart * minutesPerKm + chargeMinutesTotal,
                maxPowerKw = stop.maxPowerKw,
            )

            socNow = departureSoc
            kmNow = stop.kmFromStart
        }

        // More stops than MAX_STOPS means the loop above didn't reach the
        // destination — with a healthy vehicle profile that's a sign of a
        // degenerate input (a moped battery for a continental trip), and an
        // honest refusal beats a 14-stop plan nobody would drive.
        val reachesDestination = totalKm <= kmNow + RangeCalculator.rangeKm(vehicle, socNow)
        if (!reachesDestination) return TripPlanResult.NoChargerInReach(afterKm = kmNow)

        val arrivalSoc = RangeCalculator.socOnArrivalPercent(vehicle, socNow, totalKm - kmNow)

        return TripPlanResult.Planned(
            TripPlan(
                route = route,
                destination = destination,
                stops = stops,
                driveMinutes = route.durationMinutes,
                chargeMinutes = chargeMinutesTotal,
                arrivalSocPercent = arrivalSoc,
            ),
        )
    }

    private data class Candidate(
        val site: ChargeSite,
        val kmFromStart: Double,
        val maxPowerKw: Double,
        val operator: String?,
    )

    /**
     * Fetched segment by segment, not as one polyline over the whole route:
     * the sources query radially with a result cap (OpenChargeMapSource), and
     * a single query over a 600 km route returns an arbitrary subset of a
     * 600 km circle — of which the 3 km route buffer keeps almost nothing.
     * Chunks keep every query at the radius the sources were built for.
     */
    private suspend fun candidatesAlong(route: Route, vehicle: VehicleProfile, networks: NetworkPreferences): List<Candidate> {
        val cumulative = cumulativeDistances(route.points)
        val usable = vehicle.acceptedConnectors.ifEmpty { setOf(ConnectorType.CCS2) }
        val seen = LinkedHashMap<String, Candidate>()

        var startIndex = 0
        while (startIndex < route.points.size - 1) {
            var endIndex = startIndex + 1
            while (endIndex < route.points.size - 1 &&
                cumulative[endIndex] - cumulative[startIndex] < SEGMENT_FETCH_KM
            ) {
                endIndex++
            }
            val area = PolylineArea(route.points.subList(startIndex, endIndex + 1), bufferKm = STOP_BUFFER_KM)
            val sites = try {
                repository.sitesIn(area, networks.selectedNetworks())
            } catch (failure: Exception) {
                emptyList()
            }
            for (site in sites) {
                if (site.id in seen) continue
                val power = site.connectors
                    .filter { it.type in usable }
                    .maxOfOrNull { it.maxPowerKw }
                    ?: continue
                if (power < MIN_DC_POWER_KW) continue
                // One pass over this chunk's segments yields both the buffer
                // check and the km-from-start; the site was fetched from this
                // chunk, so there is no reason to scan the whole route for it.
                val projection = projectOntoChunk(site.position, route.points, cumulative, startIndex, endIndex)
                if (projection.distanceKm > STOP_BUFFER_KM) continue
                seen[site.id] = Candidate(
                    site = site,
                    kmFromStart = projection.kmFromStart,
                    maxPowerKw = power,
                    operator = site.operator,
                )
            }
            startIndex = endIndex
        }
        return seen.values.sortedBy { it.kmFromStart }
    }

    /**
     * The stop for one leg: as late as safely possible, preferring chargers
     * that pass the filters, among those the strongest, among equals the
     * later one. Filters are dropped only when the window would go empty.
     */
    private fun pickStop(
        candidates: List<Candidate>,
        kmNow: Double,
        reachKm: Double,
        filters: ChargeFilters,
        networks: NetworkPreferences,
    ): Candidate? {
        // Both margins scale down with the reach: with 30 km left in the
        // battery, insisting on 40 km of driving before the first stop would
        // reject a charger 5 km away — and the plan would fail exactly when
        // the driver needs it most.
        // The 15 % cap matters at the bottom end: the reach already contains
        // the 10 % reserve (RangeCalculator), and a second fat margin on a
        // 20 km reach would exclude every reachable charger.
        val reachAheadKm = reachKm - kmNow
        val minAheadKm = minOf(MIN_LEG_KM, reachAheadKm * 0.2)
        val maxAheadKm = reachAheadKm - minOf(STOP_SAFETY_KM, reachAheadKm * 0.15)
        if (maxAheadKm <= minAheadKm) return null

        val window = candidates.filter {
            it.kmFromStart > kmNow + minAheadKm && it.kmFromStart <= kmNow + maxAheadKm
        }
        if (window.isEmpty()) return null

        val preferred = window.filter {
            it.maxPowerKw >= filters.minPowerKw && networks.allowsSite(it.site)
        }
        val pool = preferred.ifEmpty { window }

        // Late beats strong, but only within the last stretch of the reach:
        // a 350 kW charger 30 km earlier wins against a 50 kW one at the edge.
        val lateStart = kmNow + maxAheadKm - LATE_WINDOW_KM
        val late = pool.filter { it.kmFromStart >= lateStart }
        return (late.ifEmpty { pool }).maxByOrNull { it.maxPowerKw * 1000.0 + it.kmFromStart }
    }

    /** Charge to what the rest of the trip needs (plus margin), never past [TARGET_SOC_PERCENT]. */
    private fun departureSocFor(
        vehicle: VehicleProfile,
        stopKm: Double,
        totalKm: Double,
        arrivalSoc: Double,
    ): Double {
        val remainingKm = totalKm - stopKm
        val neededKwh = remainingKm / 100.0 * vehicle.consumptionKwhPer100Km
        val neededSoc = neededKwh / vehicle.usableBatteryKwh * 100.0 +
            DEFAULT_RESERVE_SOC_PERCENT + CHARGE_MARGIN_SOC
        return neededSoc.coerceIn(arrivalSoc, TARGET_SOC_PERCENT)
    }

    private fun chargeMinutes(kwh: Double, sitePowerKw: Double, vehicle: VehicleProfile): Double {
        val peak = minOf(sitePowerKw, vehicle.dcPeakPowerKw ?: sitePowerKw)
        // The curve tapers hard above ~60 %; a flat factor over the whole
        // session is the workable approximation until the consumption model
        // knows real curves (ROADMAP open item 3).
        val averageKw = peak * AVERAGE_CURVE_FACTOR
        return kwh / averageKw * 60.0
    }

    private fun cumulativeDistances(points: List<LatLon>): List<Double> {
        val distances = ArrayList<Double>(points.size)
        distances += 0.0
        for (i in 1 until points.size) {
            distances += distances[i - 1] + points[i - 1].distanceKmTo(points[i])
        }
        return distances
    }

    private data class Projection(val kmFromStart: Double, val distanceKm: Double)

    /**
     * Projects [position] onto one chunk's segments — `[fromIndex, toIndex)` —
     * not the whole route. Returns the km-from-start and the perpendicular
     * distance in a single pass, so the buffer check and the position don't each
     * sweep the polyline. Scanning the full route (thousands of points) for
     * every candidate was the dominant cost of planning a long trip.
     */
    private fun projectOntoChunk(
        position: LatLon,
        points: List<LatLon>,
        cumulative: List<Double>,
        fromIndex: Int,
        toIndex: Int,
    ): Projection {
        var bestKm = cumulative[fromIndex]
        var bestDistance = Double.MAX_VALUE
        for (i in fromIndex until toIndex) {
            val distance = position.distanceKmToSegment(points[i], points[i + 1])
            if (distance < bestDistance) {
                bestDistance = distance
                val fraction = position.projectionOnSegment(points[i], points[i + 1])
                bestKm = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * fraction
            }
        }
        return Projection(bestKm, bestDistance)
    }

    private companion object {
        /** Charging past 80 % is slow enough that driving on and stopping again wins. */
        const val TARGET_SOC_PERCENT = 80.0

        /** On top of reserve + need, so a headwind doesn't turn the plan into a tow job. */
        const val CHARGE_MARGIN_SOC = 5.0

        /** Max straight-line distance from the route — same trade-off as PolylineArea (open item 8). */
        const val STOP_BUFFER_KM = 3.0

        /** Candidate-fetch chunk length — the area size the sources were built for. */
        const val SEGMENT_FETCH_KM = 80.0

        /** Don't burn a stop in the first minutes of a leg. */
        const val MIN_LEG_KM = 40.0

        /** Keep this much reach unspent when picking a stop. */
        const val STOP_SAFETY_KM = 25.0

        /** "Late" preference window at the end of the reach. */
        const val LATE_WINDOW_KM = 80.0

        /** A destination this close to the range edge doesn't need a final stop. */
        const val ARRIVAL_HEADROOM_KM = 0.0

        const val AVERAGE_CURVE_FACTOR = 0.65

        const val MAX_STOPS = 8
    }
}
