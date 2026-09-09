package de.autoapp.shared.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The fields of the OpenChargeMap POI response that are actually needed here.
 *
 * Only a fraction of what the API returns — everything else is dropped by
 * `ignoreUnknownKeys`. The capitalization of the names is OCM's own and is
 * mapped to the naming convention used here via [SerialName].
 *
 * Almost everything is nullable because it is in the data too: OCM is
 * largely community-maintained, and a site with no power rating or no
 * operator is not unusual there.
 */
@Serializable
internal data class OcmPoi(
    @SerialName("ID") val id: Long? = null,
    @SerialName("OperatorID") val operatorId: Long? = null,
    @SerialName("UUID") val uuid: String? = null,
    @SerialName("AddressInfo") val addressInfo: OcmAddressInfo? = null,
    @SerialName("OperatorInfo") val operatorInfo: OcmOperatorInfo? = null,
    @SerialName("Connections") val connections: List<OcmConnection>? = null,
)

@Serializable
internal data class OcmAddressInfo(
    @SerialName("Title") val title: String? = null,
    @SerialName("AddressLine1") val addressLine1: String? = null,
    @SerialName("Postcode") val postcode: String? = null,
    @SerialName("Town") val town: String? = null,
    @SerialName("Latitude") val latitude: Double? = null,
    @SerialName("Longitude") val longitude: Double? = null,
)

@Serializable
internal data class OcmOperatorInfo(
    @SerialName("Title") val title: String? = null,
)

@Serializable
internal data class OcmConnection(
    @SerialName("ConnectionTypeID") val connectionTypeId: Int? = null,
    @SerialName("PowerKW") val powerKw: Double? = null,
    @SerialName("Quantity") val quantity: Int? = null,
)
