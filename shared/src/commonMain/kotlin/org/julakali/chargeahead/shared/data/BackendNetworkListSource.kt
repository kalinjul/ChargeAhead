package org.julakali.chargeahead.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.julakali.chargeahead.api.NetworksResponse
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkListSource

class BackendNetworkListSource(
    private val httpClient: HttpClient,
) : NetworkListSource {

    override suspend fun networks(): List<Network> {
        val response: NetworksResponse = httpClient.get(PATH) {
        }.body()
        return response.networks.map { Network(key = it.key, name = it.name) }
    }

    private companion object {
        const val PATH = "v1/networks"
    }
}
