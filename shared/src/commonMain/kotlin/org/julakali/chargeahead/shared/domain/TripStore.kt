package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The trip last planned, one per process, so phone and car show the same one. */
class TripStore {
    private val current = MutableStateFlow<TripPlan?>(null)

    val plan: StateFlow<TripPlan?> = current.asStateFlow()

    internal fun store(plan: TripPlan) {
        current.value = plan
    }

    internal fun clear() {
        current.value = null
    }
}
