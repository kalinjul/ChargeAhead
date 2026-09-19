package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.db.ChargeSiteDatabase

suspend fun pruneCache(db: ChargeSiteDatabase, selectedKeys: Set<String>, now: Long, ttlMillis: Long) {
    val dao = db.chargeSites()
    val keep = (selectedKeys + TiledSiteRepository.ALL_NETWORKS).toList()
    dao.pruneStaleCoverage(now - ttlMillis)
    dao.pruneStaleSites(now - ttlMillis)
    dao.pruneCoverageNotIn(keep)
}
