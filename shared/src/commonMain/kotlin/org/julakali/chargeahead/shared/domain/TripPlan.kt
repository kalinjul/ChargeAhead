package org.julakali.chargeahead.shared.domain

/** One planned charging stop along a trip. */
data class PlannedStop(
    val site: ChargeSite,
    /** Position along the route, measured from the start. */
    val kmFromStart: Double,
    val arrivalSocPercent: Double,
    val departureSocPercent: Double,
    val chargeKwh: Double,
    val chargeMinutes: Double,
    /** Driving, charging and stop time until departure from this stop. */
    val etaMinutesFromStart: Double,
    val maxPowerKw: Double,
    /** Time at the stop that is not charging: leaving the road, plugging in, paying. */
    val stopMinutes: Double = 0.0,
    /**
     * How much longer the best plan without this stop takes, in real minutes
     * (charging and stops; driving is the same either way), or null when no
     * plan works without it. Penalties only steer the choice and are not in
     * here, so a stop kept for a preferred network can save less than zero.
     */
    val savesMinutes: Double? = null,
) {
    val arrivalMinutesFromStart: Double get() = etaMinutesFromStart - chargeMinutes - stopMinutes
}

data class TripPlan(
    val route: Route,
    val destination: Destination,
    val stops: List<PlannedStop>,
    val driveMinutes: Double,
    val chargeMinutes: Double,
    val arrivalSocPercent: Double,
    val stopMinutes: Double = 0.0,
) {
    val totalMinutes: Double get() = driveMinutes + chargeMinutes + stopMinutes
}

/** Result of planning, including why no plan came out. */
sealed interface TripPlanResult {
    data class Planned(val plan: TripPlan) : TripPlanResult

    data object NoVehicle : TripPlanResult

    /** No road connection, or the route service failed. */
    data object NoRoute : TripPlanResult

    /** A leg has no reachable fast charger. */
    data class NoChargerInReach(val afterKm: Double) : TripPlanResult
}

/** Finds the charging stops for a trip. */
interface TripPlanning {
    suspend fun plan(
        from: LatLon,
        destination: Destination,
        vehicle: VehicleProfile,
        startSocPercent: Double,
        arrivalSocPercent: Double = DEFAULT_ARRIVAL_SOC_PERCENT,
        filters: ChargeFilters = ChargeFilters(),
        networks: NetworkPreferences = NetworkPreferences(),
    ): TripPlanResult
}
