package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.OperatorOption
import org.julakali.chargeahead.shared.domain.OperatorOptions

/**
 * The networks that the local store already knows — the picker's list
 * before (and without) the first live fetch of the session.
 */
class OperatorCatalog(database: ChargeSiteDatabase) {

    private val dao = database.chargeSites()

    suspend fun options(): List<OperatorOption> =
        OperatorOptions.fromCounts(
            // The query filters IS NOT NULL, but Room can't see that.
            dao.operatorCounts().mapNotNull { row -> row.operator?.let { it to row.sites.toInt() } },
        )
}
