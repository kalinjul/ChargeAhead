package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MIN_DC_POWER_KW
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.VehicleProfile

/**
 * Plans a trip with the charging stops that get there soonest.
 *
 * Every choice is priced in minutes — charging along the curve, the stop
 * itself, the detour — and [ChargeStopOptimizer] finds the cheapest
 * combination exactly. Filters and network preferences are prices, not hard
 * limits: a charger outside them wins only when it saves more than its penalty,
 * so no leg is ever starved by a preference.
 */
class TripPlanner(
    private val routeEngine: RouteEngine,
    private val repository: SiteRepository,
) : TripPlanning {

    override suspend fun plan(
        from: LatLon,
        destination: Destination,
        vehicle: VehicleProfile,
        startSocPercent: Double,
        arrivalSocPercent: Double,
        filters: ChargeFilters,
        networks: NetworkPreferences,
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

        val tables = HashMap<Double, ChargeTimeTable>()
        val nodes = candidates.map { candidate ->
            ChargeStopOptimizer.Node(
                km = candidate.kmFromStart,
                energyFromStartSoc = socNeeded(consumption, route, vehicle, 0.0, candidate.kmFromStart),
                fixedMinutes = fixedMinutes(candidate, filters, networks),
                chargeTime = tables.getOrPut(acceptedPeakKw(vehicle, candidate.maxPowerKw)) {
                    chargeTimeTable(vehicle, candidate.maxPowerKw)
                },
            )
        }
        val totalEnergySoc = socNeeded(consumption, route, vehicle, 0.0, totalKm)

        val optimizer = ChargeStopOptimizer(MAX_DEPARTURE_SOC)
        fun optimize(excluded: Int? = null) = optimizer.optimize(
            nodes = nodes,
            totalEnergySoc = totalEnergySoc,
            startSoc = startSocPercent,
            reserveSoc = DEFAULT_RESERVE_SOC_PERCENT,
            arrivalSoc = arrivalReserve,
            excluded = excluded,
        )

        val found = when (val result = optimize()) {
            is ChargeStopOptimizer.Result.Unreachable -> return TripPlanResult.NoChargerInReach(result.furthestKm)
            is ChargeStopOptimizer.Result.Found -> result
        }

        val stops = mutableListOf<PlannedStop>()
        var timeAtStopsMinutes = 0.0
        for (choice in found.stops) {
            val candidate = candidates[choice.nodeIndex]
            val chargeMinutes = nodes[choice.nodeIndex].chargeTime
                .minutesBetween(choice.arrivalSoc, choice.departureSoc)
            timeAtStopsMinutes += chargeMinutes + STOP_OVERHEAD_MINUTES
            val savesMinutes = when (val without = optimize(excluded = choice.nodeIndex)) {
                is ChargeStopOptimizer.Result.Found -> minutesAtStops(nodes, without) - minutesAtStops(nodes, found)
                is ChargeStopOptimizer.Result.Unreachable -> null
            }
            stops += PlannedStop(
                site = candidate.site,
                kmFromStart = candidate.kmFromStart,
                arrivalSocPercent = choice.arrivalSoc,
                departureSocPercent = choice.departureSoc,
                chargeKwh = (choice.departureSoc - choice.arrivalSoc) / 100.0 * vehicle.usableBatteryKwh,
                chargeMinutes = chargeMinutes,
                etaMinutesFromStart = driveMinutesTo(route, candidate.kmFromStart) + timeAtStopsMinutes,
                maxPowerKw = candidate.maxPowerKw,
                stopMinutes = STOP_OVERHEAD_MINUTES,
                savesMinutes = savesMinutes,
            )
        }

        val lastDeparture = stops.lastOrNull()?.departureSocPercent ?: startSocPercent
        val lastKm = stops.lastOrNull()?.kmFromStart ?: 0.0
        return TripPlanResult.Planned(
            TripPlan(
                route = route,
                destination = destination,
                stops = stops,
                driveMinutes = route.durationMinutes,
                chargeMinutes = stops.sumOf { it.chargeMinutes },
                arrivalSocPercent = socAfter(consumption, route, vehicle, lastDeparture, lastKm, totalKm),
                stopMinutes = stops.sumOf { it.stopMinutes },
            ),
        )
    }

    /** Time actually spent at the stops of [plan] — its cost without detour and penalties. */
    private fun minutesAtStops(nodes: List<ChargeStopOptimizer.Node>, plan: ChargeStopOptimizer.Result.Found): Double =
        plan.stops.sumOf { choice ->
            nodes[choice.nodeIndex].chargeTime.minutesBetween(choice.arrivalSoc, choice.departureSoc) +
                STOP_OVERHEAD_MINUTES
        }

    /** What a stop at [candidate] costs besides charging, in minutes. */
    private fun fixedMinutes(candidate: Candidate, filters: ChargeFilters, networks: NetworkPreferences): Double {
        var minutes = STOP_OVERHEAD_MINUTES + 2.0 * candidate.detourKm / DETOUR_SPEED_KMH * 60.0
        if (!networks.allowsSite(candidate.site)) minutes += NETWORK_PENALTY_MINUTES
        if (candidate.maxPowerKw < filters.minPowerKw) minutes += WEAK_CHARGER_PENALTY_MINUTES
        return minutes
    }

    private data class Candidate(
        val site: ChargeSite,
        val kmFromStart: Double,
        val maxPowerKw: Double,
        /** Straight-line distance from the route. */
        val detourKm: Double,
    )

    /** Fetched in chunks, not as one polyline: the sources query radially with a result cap. */
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
                repository.load(area, networks.selectedNetworks())
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
                    detourKm = projection.distanceKm,
                )
            }
            startIndex = endIndex
        }
        return seen.values.sortedBy { it.kmFromStart }
    }

    private fun socNeeded(
        consumption: ConsumptionModel,
        route: Route,
        vehicle: VehicleProfile,
        fromKm: Double,
        toKm: Double,
    ): Double = consumption.energyKwh(route, fromKm, toKm) / vehicle.usableBatteryKwh * 100.0

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

    private companion object {
        /** Charging is never planned past this; the curve prices everything below it. */
        const val MAX_DEPARTURE_SOC = 100.0

        // To calibrate against real stops (#72).

        /** Leaving the road, parking, plugging in and paying — part of every stop's time. */
        const val STOP_OVERHEAD_MINUTES = 10.0

        /** What a charger outside the preferred networks has to save before it is chosen. */
        const val NETWORK_PENALTY_MINUTES = 15.0

        /** The same for a charger below the driver's minimum power. */
        const val WEAK_CHARGER_PENALTY_MINUTES = 10.0

        /** Pace of the way off the route and back, priced both ways. */
        const val DETOUR_SPEED_KMH = 30.0

        /** Max straight-line distance from the route. */
        const val STOP_BUFFER_KM = 3.0

        /** Candidate-fetch chunk length. */
        const val SEGMENT_FETCH_KM = 80.0
    }
}
