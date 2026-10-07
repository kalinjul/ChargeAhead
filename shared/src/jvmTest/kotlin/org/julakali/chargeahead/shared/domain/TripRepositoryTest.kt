package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TripRepositoryTest {

    private val storage = RecordingStorage()

    /** #133: the trip a killed process planned comes back. */
    @Test
    fun `a stored state comes back on restore`() = runBlocking {
        storage.stored = TripState(munich.destination, planned = munich)

        val repository = TripRepository(storage)
        repository.restore()

        assertEquals(TripState(munich.destination, planned = munich), repository.state.value)
    }

    @Test
    fun `restoring does not overwrite a change made in this process`() = runBlocking {
        storage.stored = TripState(munich.destination, planned = munich)
        val repository = TripRepository(storage)
        repository.update { it.planned(kiel.destination, kiel) }

        repository.restore()

        assertEquals(kiel, repository.state.value.planned)
    }

    @Test
    fun `every transition is written through`() = runBlocking {
        val repository = TripRepository(storage)

        repository.update { it.planned(munich.destination, munich) }
        assertEquals(repository.state.value, storage.stored)

        repository.update { it.committed(CommittedTrip(munich, 60.0, 1L)) }
        assertEquals(repository.state.value, storage.stored)
    }

    @Test
    fun `a failed write changes nothing`() = runBlocking {
        val repository = TripRepository(storage)
        storage.failing = true

        assertFailsWith<IllegalStateException> { repository.update { it.planned(munich.destination, munich) } }

        assertEquals(TripState(), repository.state.value)
    }

    @Test
    fun `committing takes the planned trip off the map`() {
        val trip = CommittedTrip(munich, 60.0, 1L)

        val state = TripState().planned(munich.destination, munich).committed(trip)

        assertNull(state.planned)
        assertEquals(trip, state.committed)
        assertEquals(munich.destination, state.destination)
    }

    @Test
    fun `dismissing the plan and ending the trip are independent`() {
        val trip = CommittedTrip(munich, 60.0, 1L)
        val both = TripState().committed(trip).planned(kiel.destination, kiel)

        assertEquals(TripState(kiel.destination, planned = null, committed = trip), both.planDismissed())
        assertEquals(TripState(kiel.destination, planned = kiel, committed = null), both.ended())
    }

    private class RecordingStorage(var stored: TripState? = null) : TripStorage {
        var failing = false

        override suspend fun read(): TripState? = stored

        override suspend fun write(state: TripState) {
            check(!failing) { "disk full" }
            stored = state
        }
    }

    private companion object {
        val munich = trip("München", LatLon(48.14, 11.58))
        val kiel = trip("Kiel", LatLon(54.32, 10.14))

        fun trip(name: String, position: LatLon) = TripPlan(
            route = Route(listOf(LatLon(52.52, 13.40), position), distanceKm = 500.0, durationMinutes = 300.0),
            destination = Destination(name, position),
            stops = emptyList(),
            driveMinutes = 300.0,
            chargeMinutes = 20.0,
            arrivalSocPercent = 30.0,
        )
    }
}
