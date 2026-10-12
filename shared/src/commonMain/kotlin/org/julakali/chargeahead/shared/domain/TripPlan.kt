package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** One planned charging stop along a trip. */
@Serializable
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

@Serializable
data class TripPlan(
    val route: Route,
    val destination: Destination,
    val stops: List<PlannedStop>,
    val driveMinutes: Double,
    val chargeMinutes: Double,
    val arrivalSocPercent: Double,
    val stopMinutes: Double = 0.0,
    /** e.g. "Feldstraße 66, Hamburg"; null when no address was found for the start. */
    val startAddress: String? = null,
) {
    val totalMinutes: Double get() = driveMinutes + chargeMinutes + stopMinutes

    /** The drives between start, stops and destination, one more than there are stops. */
    val legs: List<TripLeg>
        get() {
            var departedMinutes = 0.0
            var departedKm = 0.0
            val toStops = stops.map { stop ->
                TripLeg(stop.arrivalMinutesFromStart - departedMinutes, stop.kmFromStart - departedKm).also {
                    departedMinutes = stop.etaMinutesFromStart
                    departedKm = stop.kmFromStart
                }
            }
            return toStops + TripLeg(totalMinutes - departedMinutes, route.distanceKm - departedKm)
        }
}

data class TripLeg(val driveMinutes: Double, val distanceKm: Double)

/** Result of planning, including why no plan came out. */
sealed interface TripPlanResult {
    data class Planned(val plan: TripPlan) : TripPlanResult

    data object NoVehicle : TripPlanResult

    /** No road connection to the destination. */
    data object NoRoute : TripPlanResult

    /** The route server could not be reached or failed. */
    data object NoConnection : TripPlanResult

    /** A leg has no reachable fast charger. */
    data class NoChargerInReach(val afterKm: Double) : TripPlanResult
}

/** Why no plan reached the destination. */
sealed interface UnreachableTrip {
    /** [afterKm] is how far along the route any plan got, 0 when not even the first charger is in reach. */
    data class NoCharger(val afterKm: Double) : UnreachableTrip
    data object NoRoute : UnreachableTrip
    data object NoConnection : UnreachableTrip
}

/** Why this is no plan; `null` for a plan, and for a missing car, which is not about the trip. */
fun TripPlanResult.unreachable(): UnreachableTrip? = when (this) {
    is TripPlanResult.Planned, TripPlanResult.NoVehicle -> null
    is TripPlanResult.NoChargerInReach -> UnreachableTrip.NoCharger(afterKm)
    TripPlanResult.NoRoute -> UnreachableTrip.NoRoute
    TripPlanResult.NoConnection -> UnreachableTrip.NoConnection
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

/** Assumed start charge when the driver never entered one. */
const val DEFAULT_ASSUMED_SOC_PERCENT = 80.0

/** A start level the driver can enter; an empty battery plans nothing. */
val SOC_RANGE = 1..100

/**
 * Plans with the selected vehicle and the stored preferences. A null
 * [startSocPercent] takes the stored manual level, and without one
 * [DEFAULT_ASSUMED_SOC_PERCENT]. The plan carries the address of [from]
 * when [addresses] finds one in time.
 */
suspend fun TripPlanning.planWithSettings(
    vehicles: VehicleRepository,
    catalog: VehicleCatalogRepository,
    preferences: PreferencesRepository,
    addresses: ReverseGeocoder,
    from: LatLon,
    destination: Destination,
    startSocPercent: Double?,
    computation: CoroutineDispatcher,
): TripPlanResult {
    val vehicle = vehicles.vehicle.first()?.withRoadLoadFrom(catalog.presets.first()) ?: return TripPlanResult.NoVehicle
    val soc = startSocPercent
        ?: vehicles.manualSocPercent.first()
        ?: DEFAULT_ASSUMED_SOC_PERCENT
    val arrivalSoc = vehicles.arrivalSocPercent.first()
    val filters = preferences.chargeFilters.first()
    val networks = preferences.networks.first()
    return coroutineScope {
        val startAddress = async {
            withTimeoutOrNull(START_ADDRESS_TIMEOUT_MILLIS) { cancellableRunCatching { addresses.addressAt(from) }.getOrNull() }
        }
        val result = withContext(computation) {
            plan(from, destination, vehicle, soc, arrivalSoc, filters, networks)
        }
        if (result is TripPlanResult.Planned) {
            TripPlanResult.Planned(result.plan.copy(startAddress = startAddress.await()?.startLine()))
        } else {
            startAddress.cancel()
            result
        }
    }
}

private fun Address.startLine(): String? = listOfNotNull(street, town).joinToString(", ").takeIf { it.isNotBlank() }

// The plan is not held back by a slow address lookup.
private const val START_ADDRESS_TIMEOUT_MILLIS = 3_000L
