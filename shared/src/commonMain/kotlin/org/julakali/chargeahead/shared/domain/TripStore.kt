package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where the planned trip outlives the process. */
interface PlannedTripStorage {

    suspend fun read(): TripPlan?

    /** `null` drops the stored trip. */
    suspend fun write(plan: TripPlan?)

    /** Keeps nothing, so the trip only lives as long as the process. */
    object None : PlannedTripStorage {
        override suspend fun read(): TripPlan? = null

        override suspend fun write(plan: TripPlan?) = Unit
    }
}

/** The trip last planned, one per process, so phone and car show the same one. */
class TripStore(private val storage: PlannedTripStorage = PlannedTripStorage.None) {
    private val current = MutableStateFlow<TripPlan?>(null)

    val plan: StateFlow<TripPlan?> = current.asStateFlow()

    /** Brings back what an earlier process planned. A trip already in memory wins. */
    suspend fun restore() {
        val stored = storage.read() ?: return
        current.update { it ?: stored }
    }

    internal suspend fun store(plan: TripPlan) {
        current.value = plan
        storage.write(plan)
    }

    internal suspend fun clear() {
        current.value = null
        storage.write(null)
    }
}
