package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.ChargePointStatus
import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import org.julakali.chargeahead.shared.domain.ChargePointStatusSource
import org.julakali.chargeahead.shared.domain.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the most recently fetched [maxEntries] sites in memory; the oldest
 * fetch is dropped first. Stale entries are still served until a refresh replaces them.
 */
class CachingChargePointStatusRepository(
    /** `null` without a backend: no live data then. */
    private val source: ChargePointStatusSource?,
    private val time: TimeProvider,
    private val maxEntries: Int = MAX_ENTRIES,
) : ChargePointStatusRepository {

    private val cache = MutableStateFlow<Map<String, CachedStatus>>(emptyMap())
    private val fetchLock = Mutex()

    override val statuses: Flow<Map<String, List<ChargePointStatus>>> =
        cache.map { entries -> entries.mapValues { it.value.points } }

    override suspend fun refresh(ids: Collection<String>) {
        val source = source ?: return
        // One fetch at a time, so overlapping refreshes don't ask for the same ids twice.
        fetchLock.withLock {
            val now = time.nowMillis()
            val cached = cache.value
            val due = ids.distinct().filter { id ->
                val entry = cached[id]
                entry == null || now - entry.fetchedAtMillis > STALE_AFTER_MILLIS
            }
            if (due.isEmpty()) return

            val fetched = source.status(due)
            cache.update { current ->
                val next = LinkedHashMap(current)
                due.forEach { id ->
                    next.remove(id)
                    next[id] = CachedStatus(fetched[id].orEmpty(), now)
                }
                while (next.size > maxEntries) next.remove(next.keys.first())
                next
            }
        }
    }

    private data class CachedStatus(val points: List<ChargePointStatus>, val fetchedAtMillis: Long)

    companion object {
        const val STALE_AFTER_MILLIS = 60_000L
        const val MAX_ENTRIES = 1_000
    }
}
