package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlannedTripStorage
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripStoreTest {

    private val storage = RecordingStorage()

    @Test
    fun `clearing drops the stored plan`() = runBlocking {
        val store = TripStore()

        store.clear()

        assertNull(store.plan.value)
    }

    /** #133: the trip a killed process planned comes back. */
    @Test
    fun `a stored plan comes back on restore`() = runBlocking {
        storage.stored = munichTrip

        val store = TripStore(storage)
        store.restore()

        assertEquals(munichTrip, store.plan.value)
    }

    @Test
    fun `restoring does not overwrite a plan already made in this process`() = runBlocking {
        storage.stored = munichTrip
        val store = TripStore(storage)
        store.store(kielTrip)

        store.restore()

        assertEquals(kielTrip, store.plan.value)
    }

    @Test
    fun `planning and clearing are written through`() = runBlocking {
        val store = TripStore(storage)

        store.store(munichTrip)
        assertEquals(munichTrip, storage.stored)

        store.clear()
        assertNull(storage.stored)
    }

    private class RecordingStorage(var stored: TripPlan? = null) : PlannedTripStorage {
        override suspend fun read(): TripPlan? = stored

        override suspend fun write(plan: TripPlan?) {
            stored = plan
        }
    }

    private companion object {
        val munichTrip = trip("München", LatLon(48.14, 11.58))
        val kielTrip = trip("Kiel", LatLon(54.32, 10.14))

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
