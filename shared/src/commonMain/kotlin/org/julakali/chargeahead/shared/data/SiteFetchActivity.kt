package org.julakali.chargeahead.shared.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Whether any [TiledSiteRepository] is currently asking its source.
 *
 * A counter, not a flag, because the repositories fetch concurrently.
 */
class SiteFetchActivity {
    private val running = MutableStateFlow(0)

    val isFetching: Flow<Boolean> = running.map { it > 0 }.distinctUntilChanged()

    suspend fun <T> track(block: suspend () -> T): T {
        running.update { it + 1 }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }
}
