package org.julakali.chargeahead.shared.domain

/** What the app takes from a source. */
enum class DatasetKind { SITES, STATUS, ROUTING, PLACES, OTHER }

data class License(val name: String, val url: String?)

/** One kind of data from a source, under its own licence. */
data class Dataset(val kind: DatasetKind, val url: String?, val license: License?)

/** A source the app's data comes from, as the licence page credits it. */
data class DataSource(
    val id: String,
    val name: String,
    val url: String?,
    val datasets: List<Dataset>,
)

/** Where the data the app shows comes from. */
interface DataSourceDirectory {
    suspend fun dataSources(): List<DataSource>
}
