package org.julakali.chargeahead.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.julakali.chargeahead.api.ChargePointStatusDto
import org.julakali.chargeahead.api.ChargePointStatusRequest
import org.julakali.chargeahead.api.ChargePointStatusResponse
import org.julakali.chargeahead.shared.domain.ChargePointState
import org.julakali.chargeahead.shared.domain.ChargePointStatus
import org.julakali.chargeahead.shared.domain.ChargePointStatusSource

class BackendChargePointStatusSource(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String,
) : ChargePointStatusSource {

    override suspend fun status(siteIds: List<String>): Map<String, List<ChargePointStatus>> =
        siteIds.distinct().chunked(MAX_SITES_PER_REQUEST).flatMap { chunk ->
            val response: ChargePointStatusResponse = httpClient.post("${baseUrl.trimEnd('/')}$PATH") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(ChargePointStatusRequest(siteIds = chunk))
            }.body()
            response.sites.map { site -> site.siteId to site.chargePoints.map { it.toDomain() } }
        }.toMap()

    companion object {
        private const val PATH = "/v1/charge-point-status"

        /** The backend rejects larger requests. */
        const val MAX_SITES_PER_REQUEST = 500
    }
}

private fun ChargePointStatusDto.toDomain() = ChargePointStatus(
    state = when (status) {
        "available" -> ChargePointState.AVAILABLE
        "occupied" -> ChargePointState.OCCUPIED
        "reserved" -> ChargePointState.RESERVED
        "out_of_order" -> ChargePointState.OUT_OF_ORDER
        "blocked" -> ChargePointState.BLOCKED
        else -> ChargePointState.UNKNOWN
    },
    maxPowerKw = maxPowerKw,
    connectors = connectors.map { it.toDomain() },
)
