package de.autoapp.shared.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The ArcGIS response from the charging register.
 *
 * Field names with umlauts are the service's own and not negotiable.
 */
@Serializable
internal data class ArcGisResponse(
    val features: List<ArcGisFeature> = emptyList(),
    val exceededTransferLimit: Boolean = false,
    /** ArcGIS reports errors with HTTP 200 and this object. See [BnetzaSource]. */
    val error: ArcGisError? = null,
)

@Serializable
internal data class ArcGisError(
    val code: Int? = null,
    val message: String? = null,
)

@Serializable
internal data class ArcGisFeature(val attributes: BnetzaAttributes)

/**
 * Only the fields that are actually needed — the service has 48.
 *
 * Connector data comes in six side-by-side pairs instead of a list; each
 * field can also contain multiple connectors separated by "; ", and
 * `Nennleistung_Stecker{i}` carries the corresponding powers in the same order.
 */
@Serializable
internal data class BnetzaAttributes(
    @SerialName("Ladeeinrichtungs_ID") val id: Long? = null,
    @SerialName("Betreiber") val operator: String? = null,
    @SerialName("Standortbezeichnung") val locationName: String? = null,
    @SerialName("Straße") val street: String? = null,
    @SerialName("Hausnummer") val houseNumber: String? = null,
    @SerialName("Postleitzahl") val postalCode: String? = null,
    @SerialName("Ort") val town: String? = null,
    @SerialName("Breitengrad") val latitude: Double? = null,
    @SerialName("Längengrad") val longitude: Double? = null,
    @SerialName("Steckertypen1") val connectorTypes1: String? = null,
    @SerialName("Nennleistung_Stecker1") val connectorPower1: String? = null,
    @SerialName("Steckertypen2") val connectorTypes2: String? = null,
    @SerialName("Nennleistung_Stecker2") val connectorPower2: String? = null,
    @SerialName("Steckertypen3") val connectorTypes3: String? = null,
    @SerialName("Nennleistung_Stecker3") val connectorPower3: String? = null,
    @SerialName("Steckertypen4") val connectorTypes4: String? = null,
    @SerialName("Nennleistung_Stecker4") val connectorPower4: String? = null,
    @SerialName("Steckertypen5") val connectorTypes5: String? = null,
    @SerialName("Nennleistung_Stecker5") val connectorPower5: String? = null,
    @SerialName("Steckertypen6") val connectorTypes6: String? = null,
    @SerialName("Nennleistung_Stecker6") val connectorPower6: String? = null,
) {
    /** The six connector slots as pairs, empty ones skipped. */
    val connectorSlots: List<Pair<String, String?>>
        get() = listOf(
            connectorTypes1 to connectorPower1,
            connectorTypes2 to connectorPower2,
            connectorTypes3 to connectorPower3,
            connectorTypes4 to connectorPower4,
            connectorTypes5 to connectorPower5,
            connectorTypes6 to connectorPower6,
        ).mapNotNull { (types, power) ->
            types?.takeIf { it.isNotBlank() }?.let { it to power }
        }
}
