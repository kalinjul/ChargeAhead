package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.NetworkCatalog

suspend fun pruneCache(db: ChargeSiteDatabase, selectedKeys: Set<String>, now: Long, ttlMillis: Long) {
    val dao = db.chargeSites()
    val keep = (selectedKeys + NetworkCatalog.UNFILTERED).toList()
    dao.pruneStaleCoverage(now - ttlMillis)
    dao.pruneStaleSites(now - ttlMillis)
    dao.pruneCoverageNotIn(keep)
}
