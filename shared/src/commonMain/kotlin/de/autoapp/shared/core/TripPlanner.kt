package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.DEFAULT_ARRIVAL_SOC_PERCENT
import de.autoapp.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.Route
import de.autoapp.shared.domain.RouteEngine
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.VehicleProfile

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
 * Plans a trip with charging stops, chosen greedily: drive as far as the
 * battery safely allows, then take the strongest matching charger near the
 * end of that reach. Not cost-optimal; errs toward fewer, later, faster stops.
 *
 * Filters narrow the candidates but never starve a leg — a leg with no
 * matching charger falls back to any usable fast one.
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
        val minutesPerKm = route.durationMinutes / totalKm

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

            val stop = pickStop(candidates, kmNow, reachKm, filters, networks)
                ?: return TripPlanResult.NoChargerInReach(afterKm = kmNow)

            val arrivalSoc = socAfter(consumption, route, vehicle, socNow, kmNow, stop.kmFromStart)
            val departureSoc =
                departureSocFor(consumption, route, vehicle, stop.kmFromStart, totalKm, arrivalSoc, arrivalReserve)
            val chargeKwh = (departureSoc - arrivalSoc) / 100.0 * vehicle.usableBatteryKwh
            val chargeMinutes = chargeMinutes(vehicle, stop.maxPowerKw, arrivalSoc, departureSoc)
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
        val neededKwh = consumption.energyKwh(route, stopKm, totalKm)
        val driveSoc = neededKwh / vehicle.usableBatteryKwh * 100.0
        val finishesTheTrip = driveSoc + FINISH_MARGIN_SOC + DEFAULT_RESERVE_SOC_PERCENT <= TARGET_SOC_PERCENT &&
            driveSoc + arrivalReserve <= FULL_SOC_PERCENT
        val cap = if (finishesTheTrip) FULL_SOC_PERCENT else TARGET_SOC_PERCENT
        return (driveSoc + arrivalReserve).coerceAtMost(cap).coerceAtLeast(arrivalSoc)
    }

    /** Energy above [reserveSocPercent], the part that may be planned into a leg. */
    private fun availableKwh(
        vehicle: VehicleProfile,
        socPercent: Double,
        reserveSocPercent: Double = DEFAULT_RESERVE_SOC_PERCENT,
    ): Double = vehicle.usableBatteryKwh * (socPercent - reserveSocPercent).coerceAtLeast(0.0) / 100.0

    /** Relative to the full battery, like the car's own display; never below zero. */
    private fun socAfter(
        consumption: ConsumptionModel,
        route: Route,
        vehicle: VehicleProfile,
        socPercent: Double,
        fromKm: Double,
        toKm: Double,
    ): Double {
        val neededSoc = consumption.energyKwh(route, fromKm, toKm) / vehicle.usableBatteryKwh * 100.0
        return (socPercent - neededSoc).coerceAtLeast(0.0)
    }

    private companion object {
        /** Charging past 80 % is slow enough that driving on and stopping again wins. */
        const val TARGET_SOC_PERCENT = 80.0

        /** Nothing charges past this, whatever the arrival level asks for. */
        const val FULL_SOC_PERCENT = 100.0

        /** Only decides whether a stop counts as trip-finishing; never added to the charge target. */
        const val FINISH_MARGIN_SOC = 5.0

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

        /**
         * The last stop charges to exactly the arrival level, so the reach after
         * it equals the remaining distance — without this tolerance a rounding
         * difference would buy a whole extra stop.
         */
        const val ARRIVAL_HEADROOM_KM = 1.0

        const val MAX_STOPS = 8
    }
}
