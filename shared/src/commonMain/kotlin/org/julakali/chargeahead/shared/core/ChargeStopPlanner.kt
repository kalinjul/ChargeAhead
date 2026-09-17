package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.ROUTE_DETOUR_FACTOR
import org.julakali.chargeahead.shared.domain.Reachability
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.distanceKmTo

/**
 * Turns raw sites into the list that appears in the car
 */
object ChargeStopPlanner {

    fun plan(
        area: SearchArea,
        sites: List<ChargeSite>,
        vehicle: VehicleProfile? = null,
        energy: EnergyState? = null,
        reserveSocPercent: Double = DEFAULT_RESERVE_SOC_PERCENT,
        networks: NetworkPreferences = NetworkPreferences(),
        routeAhead: RouteProgress? = null,
    ): List<ChargeStop> {
        val estimate = if (routeAhead != null) AlongRoute(routeAhead) else Corridor(area)

        val rangeKm = if (vehicle != null && energy != null) {
            estimate.rangeKm(vehicle, energy.socPercent, reserveSocPercent)
        } else {
            null
        }

        return sites.asSequence()
            // The source returned a circle, not the sector — anything to the
            // side or behind the vehicle is dropped here.
            .filter { it.position in area }
            .distinctBy { it.id }
            // A charger the car can't use isn't a charge stop. Without a
            // profile, no filtering happens, or the list would be empty.
            .filter { vehicle == null || it.fitsVehicle(vehicle) }
            // A network the driver doesn't want or can't use isn't a charge
            // stop either. Unlike reachability, this hides rather than marks
            // it: it's the driver's own choice.
            .filter { networks.allowsSite(it) }
            .map { site ->
                val distanceKm = estimate.distanceKm(site)
                ChargeStop(
                    site = site,
                    distanceKm = distanceKm,
                    reachability = rangeKm?.let { ReachabilityClassifier.classify(distanceKm, it) }
                        ?: Reachability.UNKNOWN,
                    socOnArrivalPercent = if (vehicle != null && energy != null) {
                        estimate.socOnArrivalPercent(vehicle, energy.socPercent, distanceKm)
                    } else {
                        null
                    },
                    primaryConnector = site.primaryConnectorFor(vehicle),
                )
            }
            // Unreachable sites aren't hidden but pushed to the end.
            // At low charge, the list would otherwise
            // look emptied out for no reason
            .sortedWith(compareBy({ it.reachability.isOutOfRange() }, { it.distanceKm }))
            .toList()
    }

    /**
     * The strongest connector the vehicle can use.
     */
    private fun ChargeSite.primaryConnectorFor(vehicle: VehicleProfile?): Connector? {
        val usable = if (vehicle == null || vehicle.acceptedConnectors.isEmpty()) {
            connectors
        } else {
            connectors.filter { it.type in vehicle.acceptedConnectors }
        }
        return usable.ifEmpty { connectors }.maxByOrNull { it.maxPowerKw }
    }

    private fun ChargeSite.fitsVehicle(vehicle: VehicleProfile): Boolean {
        if (vehicle.acceptedConnectors.isEmpty()) return true
        // Don't filter out sites with unknown connectors: the source may
        // simply not know, and silently hiding a charger is worse than one
        // that turns out unsuitable once you're there.
        if (connectors.isEmpty()) return true
        return connectors.any { it.type in vehicle.acceptedConnectors }
    }

    private fun Reachability.isOutOfRange(): Boolean = this == Reachability.UNREACHABLE

    private interface DistanceEstimate {
        fun distanceKm(site: ChargeSite): Double
        fun rangeKm(vehicle: VehicleProfile, socPercent: Double, reserveSocPercent: Double): Double
        fun socOnArrivalPercent(vehicle: VehicleProfile, socPercent: Double, distanceKm: Double): Double
    }

    private class Corridor(private val area: SearchArea) : DistanceEstimate {
        override fun distanceKm(site: ChargeSite): Double =
            area.origin.distanceKmTo(site.position) * ROUTE_DETOUR_FACTOR

        override fun rangeKm(vehicle: VehicleProfile, socPercent: Double, reserveSocPercent: Double): Double =
            RangeCalculator.rangeKm(vehicle, socPercent, reserveSocPercent)

        override fun socOnArrivalPercent(vehicle: VehicleProfile, socPercent: Double, distanceKm: Double): Double =
            RangeCalculator.socOnArrivalPercent(vehicle, socPercent, distanceKm)
    }

    /** The site's offset beside the route is ignored, as in TripPlanner. */
    private class AlongRoute(private val progress: RouteProgress) : DistanceEstimate {
        private val route = progress.measure.route
        private val fromKm = progress.kmFromStart

        override fun distanceKm(site: ChargeSite): Double {
            val siteKm = progress.measure.project(site.position, fromIndex = progress.segmentIndex).kmFromStart
            return (siteKm - fromKm).coerceAtLeast(0.0)
        }

        override fun rangeKm(vehicle: VehicleProfile, socPercent: Double, reserveSocPercent: Double): Double {
            val availableKwh = vehicle.usableBatteryKwh * (socPercent - reserveSocPercent).coerceAtLeast(0.0) / 100.0
            return SpeedAwareConsumption(vehicle.consumptionKwhPer100Km).reachKm(route, fromKm, availableKwh) - fromKm
        }

        override fun socOnArrivalPercent(vehicle: VehicleProfile, socPercent: Double, distanceKm: Double): Double {
            val neededKwh = SpeedAwareConsumption(vehicle.consumptionKwhPer100Km)
                .energyKwh(route, fromKm, fromKm + distanceKm)
            return (socPercent - neededKwh / vehicle.usableBatteryKwh * 100.0).coerceAtLeast(0.0)
        }
    }
}
