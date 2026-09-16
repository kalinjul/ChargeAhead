package org.julakali.chargeahead.shared.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Whether any [TiledSiteRepository] is currently asking its source — the map
 * shows a spinner for it.
 *
 * A counter, not a flag: every source has its own repository and they fetch
 * concurrently, so the first one finishing must not clear the spinner while
 * another is still waiting on the network. Cache hits never pass through
 * here, so a pan over known ground stays quiet. One instance per process,
 * shared by all repositories.
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
