package org.julakali.chargeahead.shared.db

import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon

/** Stores connectors, source ids and route points as JSON columns. */
class Converters {

    @TypeConverter
    fun connectorsToJson(connectors: List<Connector>): String =
        json.encodeToString(connectors.map { StoredConnector(it.type, it.maxPowerKw, it.count) })

    @TypeConverter
    fun connectorsFromJson(value: String): List<Connector> =
        json.decodeFromString<List<StoredConnector>>(value).map { Connector(it.type, it.maxPowerKw, it.count) }

    @TypeConverter
    fun stringsToJson(values: Set<String>): String = json.encodeToString(values)

    @TypeConverter
    fun stringsFromJson(value: String): Set<String> = json.decodeFromString(value)

    @TypeConverter
    fun pointsToJson(points: List<LatLon>): String =
        json.encodeToString(points.map { StoredPoint(it.lat, it.lon) })

    @TypeConverter
    fun pointsFromJson(value: String): List<LatLon> =
        json.decodeFromString<List<StoredPoint>>(value).map { LatLon(it.lat, it.lon) }

    private companion object {
        // coerceInputValues turns an enum name this version doesn't know into
        // the property's default, so a rollback doesn't cost the whole store.
        val json = Json { coerceInputValues = true; ignoreUnknownKeys = true }
    }
}

@Serializable
private data class StoredConnector(
    val type: ConnectorType = ConnectorType.UNKNOWN,
    val maxPowerKw: Double,
    // null: the source doesn't state the quantity.
    val count: Int? = null,
)

@Serializable
private data class StoredPoint(val lat: Double, val lon: Double)
