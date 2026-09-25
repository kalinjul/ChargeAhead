package org.julakali.chargeahead.shared.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.db.PlannedTripEntity
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.PlannedTripStorage
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteSegment
import org.julakali.chargeahead.shared.domain.TripPlan

/** The planned trip as a JSON row in the app's database. */
class RoomTripStorage(private val database: ChargeSiteDatabase) : PlannedTripStorage {

    override suspend fun read(): TripPlan? {
        val raw = database.plannedTrip().load() ?: return null
        // A payload from an older or newer build costs the trip, not the launch.
        return runCatching { json.decodeFromString<StoredTrip>(raw).toDomain() }.getOrNull()
    }

    override suspend fun write(plan: TripPlan?) {
        val dao = database.plannedTrip()
        if (plan == null) dao.clear() else dao.save(PlannedTripEntity(plan = json.encodeToString(plan.toStored())))
    }

    private companion object {
        // coerceInputValues turns an enum name this version doesn't know into
        // the property's default, as in the type converters.
        val json = Json { coerceInputValues = true; ignoreUnknownKeys = true }
    }
}

@Serializable
private data class StoredPoint(val lat: Double, val lon: Double)

@Serializable
private data class StoredSegment(val fromKm: Double, val distanceKm: Double, val durationMinutes: Double)

@Serializable
private data class StoredRoute(
    val points: List<StoredPoint>,
    val distanceKm: Double,
    val durationMinutes: Double,
    val segments: List<StoredSegment> = emptyList(),
)

@Serializable
private data class StoredDestination(val name: String, val lat: Double, val lon: Double, val address: String? = null)

@Serializable
private data class StoredConnector(
    val type: ConnectorType = ConnectorType.UNKNOWN,
    val maxPowerKw: Double,
    val count: Int? = null,
)

@Serializable
private data class StoredSite(
    val id: String,
    val name: String,
    val operator: String? = null,
    val operatorId: Long? = null,
    val lat: Double,
    val lon: Double,
    val connectors: List<StoredConnector> = emptyList(),
    val street: String? = null,
    val postalCode: String? = null,
    val town: String? = null,
    val sources: Set<String> = emptySet(),
    val liveStatusId: String? = null,
    val networkKey: String? = null,
)

@Serializable
private data class StoredStop(
    val site: StoredSite,
    val kmFromStart: Double,
    val arrivalSocPercent: Double,
    val departureSocPercent: Double,
    val chargeKwh: Double,
    val chargeMinutes: Double,
    val etaMinutesFromStart: Double,
    val maxPowerKw: Double,
    val stopMinutes: Double = 0.0,
    val savesMinutes: Double? = null,
)

@Serializable
private data class StoredTrip(
    val route: StoredRoute,
    val destination: StoredDestination,
    val stops: List<StoredStop> = emptyList(),
    val driveMinutes: Double,
    val chargeMinutes: Double,
    val arrivalSocPercent: Double,
    val stopMinutes: Double = 0.0,
)

private fun TripPlan.toStored() = StoredTrip(
    route = StoredRoute(
        points = route.points.map { StoredPoint(it.lat, it.lon) },
        distanceKm = route.distanceKm,
        durationMinutes = route.durationMinutes,
        segments = route.segments.map { StoredSegment(it.fromKm, it.distanceKm, it.durationMinutes) },
    ),
    destination = StoredDestination(destination.name, destination.position.lat, destination.position.lon, destination.address),
    stops = stops.map { stop ->
        StoredStop(
            site = stop.site.toStored(),
            kmFromStart = stop.kmFromStart,
            arrivalSocPercent = stop.arrivalSocPercent,
            departureSocPercent = stop.departureSocPercent,
            chargeKwh = stop.chargeKwh,
            chargeMinutes = stop.chargeMinutes,
            etaMinutesFromStart = stop.etaMinutesFromStart,
            maxPowerKw = stop.maxPowerKw,
            stopMinutes = stop.stopMinutes,
            savesMinutes = stop.savesMinutes,
        )
    },
    driveMinutes = driveMinutes,
    chargeMinutes = chargeMinutes,
    arrivalSocPercent = arrivalSocPercent,
    stopMinutes = stopMinutes,
)

private fun ChargeSite.toStored() = StoredSite(
    id = id,
    name = name,
    operator = operator,
    operatorId = operatorId,
    lat = position.lat,
    lon = position.lon,
    connectors = connectors.map { StoredConnector(it.type, it.maxPowerKw, it.count) },
    street = address?.street,
    postalCode = address?.postalCode,
    town = address?.town,
    sources = sources,
    liveStatusId = liveStatusId,
    networkKey = networkKey,
)

/** Throws on a payload the domain types reject — a route of one point, say. */
private fun StoredTrip.toDomain() = TripPlan(
    route = Route(
        points = route.points.map { LatLon(it.lat, it.lon) },
        distanceKm = route.distanceKm,
        durationMinutes = route.durationMinutes,
        segments = route.segments.map { RouteSegment(it.fromKm, it.distanceKm, it.durationMinutes) },
    ),
    destination = Destination(destination.name, LatLon(destination.lat, destination.lon), destination.address),
    stops = stops.map { stop ->
        PlannedStop(
            site = stop.site.toDomain(),
            kmFromStart = stop.kmFromStart,
            arrivalSocPercent = stop.arrivalSocPercent,
            departureSocPercent = stop.departureSocPercent,
            chargeKwh = stop.chargeKwh,
            chargeMinutes = stop.chargeMinutes,
            etaMinutesFromStart = stop.etaMinutesFromStart,
            maxPowerKw = stop.maxPowerKw,
            stopMinutes = stop.stopMinutes,
            savesMinutes = stop.savesMinutes,
        )
    },
    driveMinutes = driveMinutes,
    chargeMinutes = chargeMinutes,
    arrivalSocPercent = arrivalSocPercent,
    stopMinutes = stopMinutes,
)

private fun StoredSite.toDomain() = ChargeSite(
    id = id,
    name = name,
    operator = operator,
    operatorId = operatorId,
    position = LatLon(lat, lon),
    connectors = connectors.map { Connector(it.type, it.maxPowerKw, it.count) },
    address = Address(street, postalCode, town).takeUnless { it.isEmpty },
    sources = sources,
    liveStatusId = liveStatusId,
    networkKey = networkKey,
)
