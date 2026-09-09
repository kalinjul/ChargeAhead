package de.autoapp.shared.data

import de.autoapp.shared.db.ChargeSiteDatabase
import de.autoapp.shared.domain.NetworkCatalog

suspend fun pruneCache(db: ChargeSiteDatabase, selectedKeys: Set<String>, now: Long, ttlMillis: Long) {
    val dao = db.chargeSites()
    val keep = (selectedKeys + NetworkCatalog.UNFILTERED).toList()
    dao.pruneStaleCoverage(now - ttlMillis)
    dao.pruneStaleSites(now - ttlMillis)
    dao.pruneCoverageNotIn(keep)
}
