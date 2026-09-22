package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.DestinationSearch
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.SubjectInteractor
import org.julakali.chargeahead.shared.domain.cancellableRunCatching
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Places matching the typed text, biased toward the current position.
 * Debounced, so typing doesn't send a request per keystroke.
 */
class DestinationSearchObserver(
    private val geocoder: Geocoder,
    private val locationSource: LocationSource,
) : SubjectInteractor<DestinationSearchObserver.Params, DestinationSearch>() {

    /** [debounce] off searches right away, for a query the driver submitted. */
    data class Params(val query: String, val debounce: Boolean = true)

    // Shown while the next query is searched, so the list doesn't blink empty on every keystroke.
    private var previous: List<Place>? = emptyList()

    override fun createObservable(params: Params): Flow<DestinationSearch> = flow {
        val query = params.query.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            previous = emptyList()
            emit(DestinationSearch(query, emptyList(), searching = false))
            return@flow
        }
        emit(DestinationSearch(query, previous, searching = true))
        if (params.debounce) delay(DEBOUNCE_MILLIS)
        val near = cancellableRunCatching { locationSource.currentFix() }.getOrNull()?.position
        val results = cancellableRunCatching { geocoder.search(query, near) }.getOrNull()
        previous = results
        emit(DestinationSearch(query, results, searching = false))
    }

    companion object {
        /** Shorter queries aren't searched. */
        const val MIN_QUERY_LENGTH = 3

        const val DEBOUNCE_MILLIS = 600L
    }
}
