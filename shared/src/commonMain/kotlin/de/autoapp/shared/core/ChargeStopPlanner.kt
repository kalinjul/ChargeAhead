package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ChargeStop
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import de.autoapp.shared.domain.EnergyState
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.ROUTE_DETOUR_FACTOR
import de.autoapp.shared.domain.Reachability
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.VehicleProfile
import de.autoapp.shared.domain.distanceKmTo

/**
 * Turns raw sites into the list that appears in the car
 * (ARCHITECTURE.md 5.3, steps 4 and 5).
 *
 * [vehicle] and [energy] are both nullable, and that's deliberate: until the
 * driver has created a profile and entered a charge level, there is nothing
 * to compute. Reachability then stays [Reachability.UNKNOWN], and the list is
 * still usable — sorted by distance as in M1.
 */
object ChargeStopPlanner {

    fun plan(
        area: SearchArea,
        sites: List<ChargeSite>,
        vehicle: VehicleProfile? = null,
        energy: EnergyState? = null,
        reserveSocPercent: Double = DEFAULT_RESERVE_SOC_PERCENT,
        networks: NetworkPreferences = NetworkPreferences(),
    ): List<ChargeStop> {
        val rangeKm = if (vehicle != null && energy != null) {
            RangeCalculator.rangeKm(vehicle, energy.socPercent, reserveSocPercent)
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
            .filter { networks.allows(it.operator) }
            .map { site ->
                val distanceKm = area.origin.distanceKmTo(site.position) * ROUTE_DETOUR_FACTOR
                ChargeStop(
                    site = site,
                    distanceKm = distanceKm,
                    reachability = rangeKm?.let { ReachabilityClassifier.classify(distanceKm, it) }
                        ?: Reachability.UNKNOWN,
                    socOnArrivalPercent = if (vehicle != null && energy != null) {
                        RangeCalculator.socOnArrivalPercent(vehicle, energy.socPercent, distanceKm)
                    } else {
                        null
                    },
                    primaryConnector = site.primaryConnectorFor(vehicle),
                )
            }
            // Unreachable sites aren't hidden but pushed to the end
            // (ARCHITECTURE.md 5.2): at low charge, the list would otherwise
            // look emptied out for no reason and the driver would lose trust.
            .sortedWith(compareBy({ it.reachability.isOutOfRange() }, { it.distanceKm }))
            .toList()
    }

    /**
     * The strongest connector the vehicle can use.
     *
     * Without this selection, a tie picks the wrong one: at the Köschinger
     * Forst service area, CCS and CHAdeMO both sit at 50 kW, and a CCS
     * vehicle got "CHAdeMO 50 kW" in its headline. Not wrong, but misleading —
     * the driver reads this as what they'll actually charge with.
     *
     * If nothing fits, power alone decides. This covers sites whose
     * connectors the source doesn't know; they deliberately pass through the
     * [fitsVehicle] filter.
     */
    private fun ChargeSite.primaryConnectorFor(vehicle: VehicleProfile?): Connector? {
        val usable = if (vehicle == null || vehicle.acceptedConnectors.isEmpty()) {
            connectors
        } else {
            connectors.filter { it.type in vehicle.acceptedConnectors }
        }
        return usable.ifEmpty { connectors }.maxByOrNull { it.maxPowerKw }
    }

    /** Does the site have at least one connector the vehicle accepts? */
    private fun ChargeSite.fitsVehicle(vehicle: VehicleProfile): Boolean {
        if (vehicle.acceptedConnectors.isEmpty()) return true
        // Don't filter out sites with unknown connectors: the source may
        // simply not know, and silently hiding a charger is worse than one
        // that turns out unsuitable once you're there.
        if (connectors.isEmpty()) return true
        return connectors.any { it.type in vehicle.acceptedConnectors }
    }

    private fun Reachability.isOutOfRange(): Boolean = this == Reachability.UNREACHABLE
}
