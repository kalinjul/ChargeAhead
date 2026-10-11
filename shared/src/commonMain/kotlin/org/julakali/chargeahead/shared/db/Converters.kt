package org.julakali.chargeahead.shared.db

import androidx.room.TypeConverter
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.Tariff
import org.julakali.chargeahead.shared.persistenceJson

/** Stores connectors, tariffs, source ids and route points as JSON columns. */
class Converters {

    @TypeConverter
    fun connectorsToJson(connectors: List<Connector>): String = persistenceJson.encodeToString(connectors)

    @TypeConverter
    fun connectorsFromJson(value: String): List<Connector> = persistenceJson.decodeFromString(value)

    @TypeConverter
    fun tariffsToJson(tariffs: List<Tariff>): String = persistenceJson.encodeToString(tariffs)

    @TypeConverter
    fun tariffsFromJson(value: String): List<Tariff> = persistenceJson.decodeFromString(value)

    @TypeConverter
    fun stringsToJson(values: Set<String>): String = persistenceJson.encodeToString(values)

    @TypeConverter
    fun stringsFromJson(value: String): Set<String> = persistenceJson.decodeFromString(value)

    @TypeConverter
    fun pointsToJson(points: List<LatLon>): String = persistenceJson.encodeToString(points)

    @TypeConverter
    fun pointsFromJson(value: String): List<LatLon> = persistenceJson.decodeFromString(value)
}
