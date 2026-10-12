package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** One planned charging stop along a trip. */
@Serializable
data class PlannedStop(
    /** Stored as a reference; a restored stop has no connectors, address or tariffs until the site store supplies them. */
    @Serializable(with = SiteReferenceSerializer::class)
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
    /** The site store was current along the route and had no site under this stop's id. */
    val siteMissing: Boolean = false,
) {
    val arrivalMinutesFromStart: Double get() = etaMinutesFromStart - chargeMinutes - stopMinutes
}

/** A site's id, and what still shows the stop when the store no longer has the site. */
@Serializable
private data class SiteReference(
    val id: String,
    val name: String,
    val operator: String? = null,
    val operatorId: Long? = null,
    val position: LatLon,
    val liveStatusId: String? = null,
    val networkKey: String? = null,
)

internal object SiteReferenceSerializer : KSerializer<ChargeSite> {
    override val descriptor: SerialDescriptor = SiteReference.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ChargeSite) = encoder.encodeSerializableValue(
        SiteReference.serializer(),
        SiteReference(value.id, value.name, value.operator, value.operatorId, value.position, value.liveStatusId, value.networkKey),
    )

    override fun deserialize(decoder: Decoder): ChargeSite = decoder.decodeSerializableValue(SiteReference.serializer()).run {
        ChargeSite(
            id = id,
            name = name,
            operator = operator,
            operatorId = operatorId,
            position = position,
            connectors = emptyList(),
            liveStatusId = liveStatusId,
            networkKey = networkKey,
        )
    }
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

    /** A stop's site is gone, so the plan no longer describes a drivable trip. */
    val needsReplan: Boolean get() = stops.any { it.siteMissing }

    /**
     * Each stop with its site from [sites]. A stop whose site is not among them keeps the one it has,
     * and is marked missing when [storeIsCurrent] says the absence is not just a gap in the store.
     */
    fun withSites(sites: Map<String, ChargeSite>, storeIsCurrent: Boolean = false): TripPlan = copy(
        stops = stops.map { stop ->
            val site = sites[stop.site.id]
            when {
                site != null -> stop.copy(site = site, siteMissing = false)
                storeIsCurrent -> stop.copy(siteMissing = true)
                else -> stop
            }
        },
    )

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

/** How far from the route a stop may lie, in a straight line. */
const val STOP_BUFFER_KM = 3.0

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
