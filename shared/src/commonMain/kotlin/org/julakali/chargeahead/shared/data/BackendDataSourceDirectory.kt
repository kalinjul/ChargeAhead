package org.julakali.chargeahead.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import org.julakali.chargeahead.api.DataSourceDto
import org.julakali.chargeahead.api.DataSourcesResponse
import org.julakali.chargeahead.api.DatasetDto
import org.julakali.chargeahead.shared.domain.DataSource
import org.julakali.chargeahead.shared.domain.DataSourceDirectory
import org.julakali.chargeahead.shared.domain.Dataset
import org.julakali.chargeahead.shared.domain.DatasetKind
import org.julakali.chargeahead.shared.domain.License

/** The sources the backend draws on; only it knows which are connected. */
class BackendDataSourceDirectory(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String,
) : DataSourceDirectory {

    override suspend fun dataSources(): List<DataSource> {
        val response: DataSourcesResponse = httpClient.get("${baseUrl.trimEnd('/')}$PATH") {
            bearerAuth(token)
        }.body()
        return response.sources.map { it.toDomain() }
    }

    private companion object {
        const val PATH = "/v1/data-sources"
    }
}

private fun DataSourceDto.toDomain() = DataSource(
    id = id,
    name = name,
    url = url,
    datasets = datasets.map { it.toDomain() },
)

private fun DatasetDto.toDomain() = Dataset(
    kind = when (kind) {
        "sites" -> DatasetKind.SITES
        "status" -> DatasetKind.STATUS
        "routing" -> DatasetKind.ROUTING
        "places" -> DatasetKind.PLACES
        else -> DatasetKind.OTHER
    },
    url = url,
    license = license?.let { License(it.name, it.url) },
)
