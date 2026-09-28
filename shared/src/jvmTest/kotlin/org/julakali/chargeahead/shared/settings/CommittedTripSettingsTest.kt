package org.julakali.chargeahead.shared.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.TripPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommittedTripSettingsTest {

    private val hamburg = LatLon(53.55, 9.99)
    private val muenchen = Destination("München", LatLon(48.137, 11.575), "Marienplatz 1, 80331 München")
    private val kassel = ChargeSite(
        id = "ocm:1",
        name = "Ionity Kassel",
        operator = "IONITY GmbH",
        position = LatLon(51.31, 9.49),
        connectors = listOf(Connector(ConnectorType.CCS2, 350.0, 6)),
        address = Address("Am Rasthof 1", "34123", "Kassel"),
        sources = setOf("ocm"),
        networkKey = "ionity",
    )
    private val plan = TripPlan(
        route = Route(listOf(hamburg, kassel.position, muenchen.position), distanceKm = 776.0, durationMinutes = 470.0),
        destination = muenchen,
        stops = listOf(
            PlannedStop(
                site = kassel,
                kmFromStart = 320.0,
                arrivalSocPercent = 18.0,
                departureSocPercent = 80.0,
                chargeKwh = 48.0,
                chargeMinutes = 24.0,
                etaMinutesFromStart = 220.0,
                maxPowerKw = 350.0,
                stopMinutes = 5.0,
                savesMinutes = 12.0,
            ),
        ),
        driveMinutes = 470.0,
        chargeMinutes = 24.0,
        arrivalSocPercent = 22.0,
        stopMinutes = 5.0,
    )

    @Test
    fun `a committed trip comes back as sent and can be cleared`() = runBlocking<Unit> {
        val preferences = InMemoryPreferencesDataStore()
        val store = PersistentSettingsStore(preferences)
        val trip = CommittedTrip(plan, startSocPercent = 62.0, committedAtEpochMillis = 1_700_000_000_000)

        store.commitTrip(trip)

        assertEquals(trip, store.committedTrip.first())
        assertEquals(trip, PersistentSettingsStore(preferences).committedTrip.first())

        store.clearCommittedTrip()
        assertNull(store.committedTrip.first())
        assertNull(PersistentSettingsStore(preferences).committedTrip.first())
    }
}
