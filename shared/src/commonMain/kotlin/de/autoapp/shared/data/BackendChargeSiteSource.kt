package de.autoapp.shared.data

import de.autoapp.shared.domain.Address
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ChargeSiteSource
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.SectorArea
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.julakali.chargeahead.api.AreaDto
import org.julakali.chargeahead.api.ChargeSiteDto
import org.julakali.chargeahead.api.ChargeSitesRequest
import org.julakali.chargeahead.api.ChargeSitesResponse
import org.julakali.chargeahead.api.ConnectorDto
import org.julakali.chargeahead.api.ConnectorTypeDto
import org.julakali.chargeahead.api.LatLonDto
import org.julakali.chargeahead.api.NetworkFilterDto

/**
 * Charging sites from the ChargeAhead backend instead of from a provider
 * directly.
 *
 * The provider keys stay on the server, and the corridor travels as a
 * polyline: the backend covers it with circles itself, so the chain of
 * requests [OpenChargeMapSource] makes becomes a single one.
 */
class BackendChargeSiteSource(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String,
) : ChargeSiteSource {

    override val id: String = SOURCE_ID

    override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> {
        val response: ChargeSitesResponse = httpClient.post("${baseUrl.trimEnd('/')}$PATH") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ChargeSitesRequest(area = area.toDto(), networks = networks.map { it.toDto() }))
        }.body()

        return response.sites.map { it.toDomain() }
    }

    companion object {
        const val SOURCE_ID: String = "chargeahead"
        private const val PATH = "/v1/charge-sites"
    }
}

private fun SearchArea.toDto(): AreaDto = when (this) {
    is PolylineArea -> AreaDto.Polyline(points = points.map { it.toDto() }, bufferKm = bufferKm)
    is SectorArea -> AreaDto.Sector(
        origin = origin.toDto(),
        bearingDeg = bearingDeg,
        halfAngleDeg = halfAngleDeg,
        radiusKm = radiusKm,
    )
    // A shape the contract does not know is sent as the circle around it:
    // fetching too much is recoverable, silently dropping the area is not.
    else -> AreaDto.Sector(
        origin = origin.toDto(),
        bearingDeg = 0.0,
        halfAngleDeg = 180.0,
        radiusKm = radiusKm,
    )
}

private fun LatLon.toDto() = LatLonDto(lat = lat, lon = lon)

private fun Network.toDto() = NetworkFilterDto(
    key = key,
    operatorIds = operatorIds,
    nameKeywords = nameKeywords,
)

private fun ChargeSiteDto.toDomain() = ChargeSite(
    id = id,
    name = name,
    operator = operator,
    operatorId = operatorId,
    position = LatLon(position.lat, position.lon),
    connectors = connectors.map { it.toDomain() },
    address = address?.let { Address(street = it.street, postalCode = it.postalCode, town = it.town) },
    // The backend names the upstream, which is what the app's merge and the
    // operator catalogue key on.
    sources = sources.ifEmpty { setOf(BackendChargeSiteSource.SOURCE_ID) },
)

private fun ConnectorDto.toDomain() = Connector(
    type = when (type) {
        ConnectorTypeDto.CCS2 -> ConnectorType.CCS2
        ConnectorTypeDto.TYPE2 -> ConnectorType.TYPE2
        ConnectorTypeDto.CHADEMO -> ConnectorType.CHADEMO
        ConnectorTypeDto.TESLA_NACS -> ConnectorType.TESLA_NACS
        ConnectorTypeDto.SCHUKO -> ConnectorType.SCHUKO
        ConnectorTypeDto.UNKNOWN -> ConnectorType.UNKNOWN
    },
    maxPowerKw = maxPowerKw,
    count = count,
)
