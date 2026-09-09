package de.autoapp.shared.data

import de.autoapp.shared.db.ChargeSiteDatabase
import de.autoapp.shared.domain.OperatorOption
import de.autoapp.shared.domain.OperatorOptions

/**
 * The networks that the local store already knows — the picker's list
 * before (and without) the first live fetch of the session.
 */
class OperatorCatalog(database: ChargeSiteDatabase) {

    private val dao = database.chargeSites()

    suspend fun options(): List<OperatorOption> =
        OperatorOptions.fromCounts(
            // The query filters IS NOT NULL, but Room can't see that through
            // the projection — hence the mapNotNull instead of a map.
            dao.operatorCounts().mapNotNull { row -> row.operator?.let { it to row.sites.toInt() } },
        )
}
