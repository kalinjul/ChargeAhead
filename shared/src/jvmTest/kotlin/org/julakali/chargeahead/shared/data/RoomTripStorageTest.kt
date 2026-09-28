package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.db.PlannedTripEntity
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteSegment
import org.julakali.chargeahead.shared.domain.TripPlan
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomTripStorageTest {

    private val database: ChargeSiteDatabase = createChargeSiteDatabase(DatabaseFactory())
    private val storage = RoomTripStorage(database)

    @Test
    fun `a plan survives the round trip unchanged`() = runBlocking {
        storage.write(trip)

        assertEquals(trip, storage.read())
    }

    @Test
    fun `nothing stored means no trip`() = runBlocking {
        assertNull(storage.read())
    }

    @Test
    fun `the newest plan replaces the previous one`() = runBlocking {
        storage.write(trip)

        val other = trip.copy(destination = Destination("Kiel", LatLon(54.32, 10.14)))
        storage.write(other)

        assertEquals(other, storage.read())
    }

    @Test
    fun `writing null drops the trip`() = runBlocking {
        storage.write(trip)

        storage.write(null)

        assertNull(storage.read())
    }

    /** A payload from a build that shaped the trip differently must not break the launch. */
    @Test
    fun `a corrupt payload reads as no trip`() = runBlocking {
        database.plannedTrip().save(PlannedTripEntity(plan = """{"route":{"points":[]}}"""))

        assertNull(storage.read())
    }

    private companion object {
        val trip = TripPlan(
            route = Route(
                points = listOf(LatLon(52.52, 13.40), LatLon(50.11, 8.68), LatLon(48.14, 11.58)),
                distanceKm = 585.0,
                durationMinutes = 351.0,
                segments = listOf(RouteSegment(fromKm = 0.0, distanceKm = 300.0, durationMinutes = 180.0)),
            ),
            destination = Destination("München", LatLon(48.14, 11.58), address = "Marienplatz 1, 80331 München"),
            stops = listOf(
                PlannedStop(
                    site = ChargeSite(
                        id = "bnetza:1141226",
                        name = "EnBW Hermsdorfer Kreuz",
                        operator = "EnBW",
                        operatorId = 42L,
                        position = LatLon(50.88, 11.78),
                        connectors = listOf(Connector(ConnectorType.CCS2, 300.0, count = 4)),
                        address = Address(street = "Rasthof 1", postalCode = "07629", town = "Hermsdorf"),
                        sources = setOf("bnetza", "datex"),
                        liveStatusId = "enbw:1234",
                        networkKey = "enbw",
                    ),
                    kmFromStart = 250.0,
                    arrivalSocPercent = 18.0,
                    departureSocPercent = 80.0,
                    chargeKwh = 33.5,
                    chargeMinutes = 22.0,
                    etaMinutesFromStart = 172.0,
                    maxPowerKw = 300.0,
                    stopMinutes = 5.0,
                    savesMinutes = 12.0,
                ),
            ),
            driveMinutes = 351.0,
            chargeMinutes = 22.0,
            arrivalSocPercent = 27.0,
            stopMinutes = 5.0,
        )
    }
}
