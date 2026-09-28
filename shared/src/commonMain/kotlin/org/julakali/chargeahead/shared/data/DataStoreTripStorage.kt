package org.julakali.chargeahead.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteSegment
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripState
import org.julakali.chargeahead.shared.domain.TripStorage

/** Trip state that older builds kept among the settings. */
interface LegacyTripSource {
    suspend fun legacyTrip(): TripState?

    suspend fun clearLegacyTrip()
}

/**
 * The trip state as one JSON entry in its own file, so a write replaces all
 * of it at once. Takes over [legacy]'s trip when nothing is stored yet.
 */
class DataStoreTripStorage(
    private val dataStore: DataStore<Preferences>,
    private val legacy: LegacyTripSource? = null,
) : TripStorage {

    override suspend fun read(): TripState? {
        val raw = dataStore.data.first()[KEY_STATE] ?: return takeLegacy()
        // A payload from an older or newer build costs the trip, not the launch.
        return runCatching { json.decodeFromString<StoredTripState>(raw).toDomain() }.getOrNull()
    }

    override suspend fun write(state: TripState) {
        val raw = json.encodeToString(state.toStored())
        dataStore.edit { it[KEY_STATE] = raw }
    }

    // Written here before it is cleared there, so a crash in between loses nothing.
    private suspend fun takeLegacy(): TripState? {
        val taken = legacy?.legacyTrip() ?: return null
        write(taken)
        legacy.clearLegacyTrip()
        return taken
    }

    private companion object {
        val KEY_STATE = stringPreferencesKey("trip.state")

        // coerceInputValues turns an enum name this version doesn't know into
        // the property's default, as in the type converters.
        val json = Json { coerceInputValues = true; ignoreUnknownKeys = true }
    }
}

@Serializable
private data class StoredTripState(
    val destination: StoredDestination? = null,
    val planned: StoredTrip? = null,
    val committed: StoredCommittedTrip? = null,
)

@Serializable
private data class StoredCommittedTrip(
    val plan: StoredTrip,
    val startSocPercent: Double? = null,
    val committedAtEpochMillis: Long,
)

private fun TripState.toStored() = StoredTripState(
    destination = destination?.toStored(),
    planned = planned?.toStored(),
    committed = committed?.let { StoredCommittedTrip(it.plan.toStored(), it.startSocPercent, it.committedAtEpochMillis) },
)

/** Throws on a payload the domain types reject — a route of one point, say. */
private fun StoredTripState.toDomain() = TripState(
    destination = destination?.toDomain(),
    planned = planned?.toDomain(),
    committed = committed?.let { CommittedTrip(it.plan.toDomain(), it.startSocPercent, it.committedAtEpochMillis) },
)

private fun Destination.toStored() = StoredDestination(name, position.lat, position.lon, address)

private fun StoredDestination.toDomain() = Destination(name, LatLon(lat, lon), address)

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
    destination = destination.toStored(),
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

private fun StoredTrip.toDomain() = TripPlan(
    route = Route(
        points = route.points.map { LatLon(it.lat, it.lon) },
        distanceKm = route.distanceKm,
        durationMinutes = route.durationMinutes,
        segments = route.segments.map { RouteSegment(it.fromKm, it.distanceKm, it.durationMinutes) },
    ),
    destination = destination.toDomain(),
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
