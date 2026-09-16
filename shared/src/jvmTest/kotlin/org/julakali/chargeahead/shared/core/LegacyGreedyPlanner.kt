package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.DEFAULT_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.VehicleProfile

/**
 * The greedy planner [TripPlanner] replaced in #72, frozen as it was at
 * 68021d6 — kept only as the benchmark the optimal planner must never lose to.
 */
class LegacyGreedyPlanner(
    private val routeEngine: RouteEngine,
    private val repository: SiteRepository,
) {

    suspend fun plan(
        from: LatLon,
        destination: Destination,
        vehicle: VehicleProfile,
        startSocPercent: Double,
        arrivalSocPercent: Double = DEFAULT_ARRIVAL_SOC_PERCENT,
        filters: ChargeFilters = ChargeFilters(),
        networks: NetworkPreferences = NetworkPreferences(),
    ): TripPlanResult {
        val route = try {
            routeEngine.route(from, destination.position)
        } catch (failure: Exception) {
            null
        } ?: return TripPlanResult.NoRoute

        val candidates = candidatesAlong(route, vehicle, networks)

        val consumption = SpeedAwareConsumption(vehicle.consumptionKwhPer100Km)
        val totalKm = route.distanceKm

        // What has to be left at the destination, never below the reserve.
        val arrivalReserve = maxOf(arrivalSocPercent, DEFAULT_RESERVE_SOC_PERCENT)

        val stops = mutableListOf<PlannedStop>()
        var socNow = startSocPercent
        var kmNow = 0.0
        var chargeMinutesTotal = 0.0

        while (stops.size < MAX_STOPS) {
            val reachKm = consumption.reachKm(route, kmNow, availableKwh(vehicle, socNow))
            // Done only if the battery still holds the arrival level there.
            val reachWithArrivalLevel = consumption.reachKm(route, kmNow, availableKwh(vehicle, socNow, arrivalReserve))
            if (totalKm <= reachWithArrivalLevel + ARRIVAL_HEADROOM_KM) break

            val next = pickStop(candidates, kmNow, reachKm, filters, networks)?.let { candidate ->
                val arrivalSoc = socAfter(consumption, route, vehicle, socNow, kmNow, candidate.kmFromStart)
                val departureSoc = departureSocFor(
                    consumption, route, vehicle, candidate.kmFromStart, totalKm, arrivalSoc, arrivalReserve,
                )
                val chargeMinutes = legacyChargeMinutes(vehicle, candidate.maxPowerKw, arrivalSoc, departureSoc)
                PlannedStop(
                    site = candidate.site,
                    kmFromStart = candidate.kmFromStart,
                    arrivalSocPercent = arrivalSoc,
                    departureSocPercent = departureSoc,
                    chargeKwh = (departureSoc - arrivalSoc) / 100.0 * vehicle.usableBatteryKwh,
                    chargeMinutes = chargeMinutes,
                    etaMinutesFromStart = driveMinutesTo(route, candidate.kmFromStart) + chargeMinutesTotal + chargeMinutes,
                    maxPowerKw = candidate.maxPowerKw,
                )
            }

            // A stop for the last few percent costs more than the previous
            // stop charging a little further up the slow part of the curve.
            val previous = stops.lastOrNull()
            val stretched = previous?.let { stretchedToFinish(consumption, route, vehicle, it, totalKm, arrivalReserve) }
            if (previous != null && stretched != null) {
                val extraMinutes = stretched.chargeMinutes - previous.chargeMinutes
                // A new stop that doesn't finish the trip will cost yet another one.
                val stretchWins = next == null ||
                    next.departureSocPercent + SOC_EPSILON <
                    socNeeded(consumption, route, vehicle, next.kmFromStart, totalKm) + arrivalReserve ||
                    extraMinutes <= next.chargeMinutes + STOP_OVERHEAD_MINUTES
                if (stretchWins) {
                    stops[stops.lastIndex] = stretched
                    chargeMinutesTotal += extraMinutes
                    socNow = stretched.departureSocPercent
                    break
                }
            }

            next ?: return TripPlanResult.NoChargerInReach(afterKm = kmNow)
            stops += next
            chargeMinutesTotal += next.chargeMinutes
            socNow = next.departureSocPercent
            kmNow = next.kmFromStart
        }

        // Hitting MAX_STOPS without arriving means degenerate input; refuse
        // rather than emit a 14-stop plan nobody would drive.
        val reachesDestination =
            totalKm <= consumption.reachKm(route, kmNow, availableKwh(vehicle, socNow, arrivalReserve)) + ARRIVAL_HEADROOM_KM
        if (!reachesDestination) return TripPlanResult.NoChargerInReach(afterKm = kmNow)

        val arrivalSoc = socAfter(consumption, route, vehicle, socNow, kmNow, totalKm)

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
     * Fetched in chunks, not as one polyline: the sources query radially with
     * a result cap, so a single query over a long route returns an arbitrary
     * subset of a huge circle, of which the route buffer keeps almost nothing.
     */
    private suspend fun candidatesAlong(route: Route, vehicle: VehicleProfile, networks: NetworkPreferences): List<Candidate> {
        val measure = RouteMeasure(route)
        val cumulative = measure.cumulativeKm
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
                val projection = measure.project(site.position, startIndex, endIndex)
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
     * The stop for one leg: as late as safely possible, strongest first,
     * filters dropped only when the window would otherwise go empty.
     */
    private fun pickStop(
        candidates: List<Candidate>,
        kmNow: Double,
        reachKm: Double,
        filters: ChargeFilters,
        networks: NetworkPreferences,
    ): Candidate? {
        // Both margins scale down with the reach: fixed ones would reject
        // every reachable charger when little battery is left.
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

        // Late beats strong, but only within the last stretch of the reach.
        val lateStart = kmNow + maxAheadKm - LATE_WINDOW_KM
        val late = pool.filter { it.kmFromStart >= lateStart }
        return (late.ifEmpty { pool }).maxByOrNull { it.maxPowerKw * 1000.0 + it.kmFromStart }
    }

    /**
     * Charge to what the rest of the trip needs plus the arrival level, and
     * nothing more — a margin on top would put the planned arrival above the
     * level the driver just set. Only the stop that finishes the trip may
     * pass [TARGET_SOC_PERCENT]; capping that one would cost an extra stop.
     * A stop that cannot reach the arrival level even at 100 % does not count
     * as finishing: it needs another stop anyway, and lifting the cap would
     * only buy the slowest part of the curve.
     */
    private fun departureSocFor(
        consumption: ConsumptionModel,
        route: Route,
        vehicle: VehicleProfile,
        stopKm: Double,
        totalKm: Double,
        arrivalSoc: Double,
        arrivalReserve: Double,
    ): Double {
        val driveSoc = socNeeded(consumption, route, vehicle, stopKm, totalKm)
        val finishesTheTrip = driveSoc + FINISH_MARGIN_SOC + DEFAULT_RESERVE_SOC_PERCENT <= TARGET_SOC_PERCENT &&
            driveSoc + arrivalReserve <= FULL_SOC_PERCENT
        val cap = if (finishesTheTrip) FULL_SOC_PERCENT else TARGET_SOC_PERCENT
        return (driveSoc + arrivalReserve).coerceAtMost(cap).coerceAtLeast(arrivalSoc)
    }

    /**
     * [previous] charged on until it finishes the trip, or null when that
     * would take it past [STRETCH_LIMIT_SOC] — beyond that, the slow end of
     * the curve is no longer a fair trade for a stop.
     */
    private fun stretchedToFinish(
        consumption: ConsumptionModel,
        route: Route,
        vehicle: VehicleProfile,
        previous: PlannedStop,
        totalKm: Double,
        arrivalReserve: Double,
    ): PlannedStop? {
        val departureSoc = socNeeded(consumption, route, vehicle, previous.kmFromStart, totalKm) + arrivalReserve
        if (departureSoc > STRETCH_LIMIT_SOC || departureSoc <= previous.departureSocPercent) return null
        val chargeMinutes = legacyChargeMinutes(vehicle, previous.maxPowerKw, previous.arrivalSocPercent, departureSoc)
        return previous.copy(
            departureSocPercent = departureSoc,
            chargeKwh = (departureSoc - previous.arrivalSocPercent) / 100.0 * vehicle.usableBatteryKwh,
            chargeMinutes = chargeMinutes,
            etaMinutesFromStart = previous.etaMinutesFromStart - previous.chargeMinutes + chargeMinutes,
        )
    }

    private fun socNeeded(
        consumption: ConsumptionModel,
        route: Route,
        vehicle: VehicleProfile,
        fromKm: Double,
        toKm: Double,
    ): Double = consumption.energyKwh(route, fromKm, toKm) / vehicle.usableBatteryKwh * 100.0

    /** Energy above [reserveSocPercent], the part that may be planned into a leg. */
    private fun availableKwh(
        vehicle: VehicleProfile,
        socPercent: Double,
        reserveSocPercent: Double = DEFAULT_RESERVE_SOC_PERCENT,
    ): Double = vehicle.usableBatteryKwh * (socPercent - reserveSocPercent).coerceAtLeast(0.0) / 100.0

    /**
     * Driving time from the start to [km], on the same speed profile the energy
     * is priced on. Whatever the segments don't cover — all of it, on a route
     * without them — runs at the route average.
     */
    private fun driveMinutesTo(route: Route, km: Double): Double {
        val to = km.coerceIn(0.0, route.distanceKm)
        val averageMinutesPerKm = if (route.distanceKm <= 0.0) 0.0 else route.durationMinutes / route.distanceKm
        var minutes = 0.0
        var coveredKm = 0.0
        for (segment in route.segments) {
            val end = minOf(segment.fromKm + segment.distanceKm, to)
            val overlap = end - maxOf(segment.fromKm, 0.0)
            if (overlap <= 0.0 || segment.distanceKm <= 0.0) continue
            minutes += segment.durationMinutes * overlap / segment.distanceKm
            coveredKm += overlap
        }
        return minutes + (to - coveredKm).coerceAtLeast(0.0) * averageMinutesPerKm
    }

    /** Relative to the full battery, like the car's own display; never below zero. */
    private fun socAfter(
        consumption: ConsumptionModel,
        route: Route,
        vehicle: VehicleProfile,
        socPercent: Double,
        fromKm: Double,
        toKm: Double,
    ): Double {
        return (socPercent - socNeeded(consumption, route, vehicle, fromKm, toKm)).coerceAtLeast(0.0)
    }

    /** The 1 % midpoint sum [chargeMinutes] used before it became additive. */
    private fun legacyChargeMinutes(vehicle: VehicleProfile, sitePowerKw: Double, fromSocPercent: Double, toSocPercent: Double): Double {
        val from = fromSocPercent.coerceIn(0.0, 100.0)
        val to = toSocPercent.coerceIn(0.0, 100.0)
        if (to <= from || sitePowerKw <= 0.0) return 0.0
        val peakKw = acceptedPeakKw(vehicle, sitePowerKw)
        if (peakKw <= 0.0) return 0.0
        var minutes = 0.0
        var soc = from
        while (soc < to) {
            val step = minOf(1.0, to - soc)
            val powerKw = peakKw * GenericChargeCurve.fractionOfPeakAt(soc + step / 2.0)
            minutes += vehicle.usableBatteryKwh * step / 100.0 / powerKw * 60.0
            soc += step
        }
        return minutes
    }

    companion object {
        /** Charging past 80 % is slow enough that driving on and stopping again wins. */
        private const val TARGET_SOC_PERCENT = 80.0

        /** Nothing charges past this, whatever the arrival level asks for. */
        private const val FULL_SOC_PERCENT = 100.0

        /**
         * How far a stop may charge past [TARGET_SOC_PERCENT] to save the stop
         * after it. On the generic curve 80 → 90 % takes about as long as a
         * short stop's overhead plus its charge; past that, the stop wins.
         */
        private const val STRETCH_LIMIT_SOC = 90.0

        /**
         * Leaving the road, parking, plugging in and back — not part of the
         * plan's times, only of the choice whether a stop is worth it.
         */
        internal const val STOP_OVERHEAD_MINUTES = 10.0

        private const val SOC_EPSILON = 1e-6

        /** Only decides whether a stop counts as trip-finishing; never added to the charge target. */
        private const val FINISH_MARGIN_SOC = 5.0

        /** Max straight-line distance from the route — same trade-off as PolylineArea (open item 8). */
        private const val STOP_BUFFER_KM = 3.0

        /** Candidate-fetch chunk length — the area size the sources were built for. */
        private const val SEGMENT_FETCH_KM = 80.0

        /** Don't burn a stop in the first minutes of a leg. */
        private const val MIN_LEG_KM = 40.0

        /** Keep this much reach unspent when picking a stop. */
        private const val STOP_SAFETY_KM = 25.0

        /** "Late" preference window at the end of the reach. */
        private const val LATE_WINDOW_KM = 80.0

        /**
         * The last stop charges to exactly the arrival level, so the reach after
         * it equals the remaining distance — without this tolerance a rounding
         * difference would buy a whole extra stop.
         */
        private const val ARRIVAL_HEADROOM_KM = 1.0

        private const val MAX_STOPS = 8
    }
}
