package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.CorridorPlanning
import org.julakali.chargeahead.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteProvider
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.VehicleProfile

class CorridorPlanner(
    private val routeProvider: RouteProvider = CorridorRouteProvider(),
    private val routeBufferKm: Double = RoutedRouteProvider.DEFAULT_BUFFER_KM,
    private val reserveSocPercent: Double = DEFAULT_RESERVE_SOC_PERCENT,
) : CorridorPlanning {

    override fun corridor(route: Route?): CorridorPlanning.Corridor {
        val routed = route?.let { RoutedRouteProvider(it, bufferKm = routeBufferKm, fallback = routeProvider) }
        return object : CorridorPlanning.Corridor {
            override fun searchArea(fix: Fix, vehicle: VehicleProfile?, energy: EnergyState?): SearchArea =
                (routed ?: routeProvider).searchArea(fix, rangeKm(vehicle, energy))

            override fun stops(
                fix: Fix,
                area: SearchArea,
                sites: List<ChargeSite>,
                vehicle: VehicleProfile?,
                energy: EnergyState?,
                networks: NetworkPreferences,
            ): List<ChargeStop> = ChargeStopPlanner.plan(
                area = area,
                sites = sites,
                vehicle = vehicle,
                energy = energy,
                reserveSocPercent = reserveSocPercent,
                networks = networks,
                routeAhead = routed?.progressAt(fix),
            )
        }
    }

    /** Range from vehicle profile and charge state — or the fallback as long as either is missing. */
    private fun rangeKm(vehicle: VehicleProfile?, energy: EnergyState?): Double {
        if (vehicle == null || energy == null) return FALLBACK_RANGE_KM
        // Below the reserve, keep searching; classification says nothing is reachable.
        return RangeCalculator.rangeKm(vehicle, energy.socPercent, reserveSocPercent)
            .coerceAtLeast(MIN_SEARCH_RANGE_KM)
    }

    companion object {
        /** Search radius as long as vehicle profile or charge state is missing; above the corridor cap. */
        const val FALLBACK_RANGE_KM = 300.0

        /** Smallest search radius, even with an empty battery. */
        const val MIN_SEARCH_RANGE_KM = 25.0
    }
}
