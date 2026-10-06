package org.julakali.chargeahead.shared.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where the driver is headed. [planned] is the trip on the map, [committed]
 * the one sent to Maps; both can exist at once.
 */
// Persisted as JSON, like everything it holds: a new field needs a default or old data reads as none.
@Serializable
data class TripState(
    /** The last destination planned to, even when that plan failed. */
    val destination: Destination? = null,
    val planned: TripPlan? = null,
    val committed: CommittedTrip? = null,
    /** No plan reached [destination]. Not kept across a restart: the level or the connection will have changed. */
    @Transient val unreachable: UnreachableTrip? = null,
) {

    fun planned(destination: Destination, plan: TripPlan): TripState =
        copy(destination = destination, planned = plan, unreachable = null)

    /** The old plan goes: it was made for a level or a destination that no longer holds. */
    fun unreachable(destination: Destination, why: UnreachableTrip): TripState =
        copy(destination = destination, planned = null, unreachable = why)

    /** The plan's destination, or the one no plan reached. */
    val currentDestination: Destination? get() = planned?.destination ?: destination.takeIf { unreachable != null }

    /** Replaces the committed trip and takes the planned one off the map. */
    fun committed(trip: CommittedTrip): TripState =
        copy(destination = trip.plan.destination, planned = null, committed = trip, unreachable = null)

    /** A committed trip planned anew; what is on the map stays. */
    fun replanned(trip: CommittedTrip): TripState = copy(committed = trip)

    fun planDismissed(): TripState = copy(planned = null, unreachable = null)

    fun ended(): TripState = copy(committed = null)
}

/** Where the trip state outlives the process. */
interface TripStorage {

    suspend fun read(): TripState?

    suspend fun write(state: TripState)

    /** Keeps nothing, so the trip only lives as long as the process. */
    object None : TripStorage {
        override suspend fun read(): TripState? = null

        override suspend fun write(state: TripState) = Unit
    }
}

/** The trip state, one per process, so phone and car show the same one. */
class TripRepository(private val storage: TripStorage = TripStorage.None) {
    private val current = MutableStateFlow(TripState())
    private val writes = Mutex()

    val state: StateFlow<TripState> = current.asStateFlow()

    /** Brings back what an earlier process stored. A change made in this process wins. */
    suspend fun restore() {
        writes.withLock {
            val stored = storage.read() ?: return
            current.compareAndSet(TripState(), stored)
        }
    }

    /** Stored before it is published, so memory never runs ahead of storage. */
    internal suspend fun update(transition: (TripState) -> TripState): TripState = writes.withLock {
        val next = transition(current.value)
        storage.write(next)
        current.value = next
        next
    }
}
