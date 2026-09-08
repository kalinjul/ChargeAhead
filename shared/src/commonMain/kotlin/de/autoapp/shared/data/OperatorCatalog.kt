package de.autoapp.shared.data

import de.autoapp.shared.db.ChargeSiteDatabase
import de.autoapp.shared.domain.OperatorOption
import de.autoapp.shared.domain.OperatorOptions

/**
 * The networks that the local store already knows — the picker's list
 * before (and without) the first live fetch of the session.
 */
class OperatorCatalog(database: ChargeSiteDatabase) {

    private val queries = database.chargeSitesQueries

    suspend fun options(): List<OperatorOption> =
        OperatorOptions.fromCounts(
            queries.operatorCounts().executeAsList().map { row ->
                row.operator_ to row.sites.toInt()
            },
        )
}
