package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.CommittedTrip
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.STOP_BUFFER_KM
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #196 */
class RefreshTripSitesInteractorTest {

    /** A store that only has [fresh] once the route was loaded. */
    private class FakeSites(private var fresh: ChargeSite?) : SiteRepository {
        val loaded = mutableListOf<Pair<SearchArea, Set<String>>>()
        var broken = false

        /** The fetch failed, but older sites in the area let the load pass. */
        var servedFromStock = false

        fun siteGone() {
            fresh = null
        }

        override suspend fun load(area: SearchArea, networkKeys: Set<String>) {
            if (broken) error("no connection")
            loaded += area to networkKeys
        }

        override suspend fun covers(area: SearchArea, networkKeys: Set<String>): Boolean = !servedFromStock

        override suspend fun storedSites(ids: Set<String>): Map<String, ChargeSite> =
            if (loaded.isEmpty()) emptyMap() else listOfNotNull(fresh).associateBy { it.id }.filterKeys { it in ids }

        override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = flowOf(emptyList())
        override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = flowOf(emptyList())
        override suspend fun invalidate() = Unit
    }

    private val sites = FakeSites(kassel)
    private val trips = TripRepository(sites = sites::storedSites)
    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val refresh = RefreshTripSitesInteractor(trips, sites, preferences)

    @Test
    fun `the stops get what the store has after refilling it along the route`() = runBlocking {
        trips.update { it.committed(CommittedTrip(viaKassel, 60.0, 1L)) }
        preferences.setNetworks(NetworkPreferences(preferredOperators = setOf("ionity"), onlyPreferred = true))

        refresh(Unit).getOrThrow()

        assertEquals(kassel, trips.state.value.committed?.plan?.stops?.single()?.site)
        assertEquals(listOf(PolylineArea(viaKassel.route.points, STOP_BUFFER_KM) to setOf("ionity")), sites.loaded.toList())
    }

    @Test
    fun `without a connection the stops stay as they are`() = runBlocking {
        trips.update { it.planned(viaKassel.destination, viaKassel) }
        sites.broken = true

        refresh(Unit).getOrThrow()

        assertEquals(viaKassel, trips.state.value.planned)
    }

    @Test
    fun `a stop the refilled store no longer has is marked missing`() = runBlocking {
        trips.update { it.committed(CommittedTrip(viaKassel, 60.0, 1L)) }
        sites.siteGone()

        refresh(Unit).getOrThrow()

        val plan = trips.state.value.committed!!.plan
        assertTrue(plan.needsReplan)
        assertEquals(viaKassel.stops.single().copy(siteMissing = true), plan.stops.single())
    }

    @Test
    fun `a stop is not missing while the store could not be refilled`() = runBlocking {
        trips.update { it.committed(CommittedTrip(viaKassel, 60.0, 1L)) }
        sites.siteGone()
        sites.servedFromStock = true

        refresh(Unit).getOrThrow()

        assertFalse(trips.state.value.committed!!.plan.needsReplan)
    }

    @Test
    fun `a missing stop the store has again is no longer missing`() = runBlocking {
        val flagged = viaKassel.copy(stops = viaKassel.stops.map { it.copy(siteMissing = true) })
        trips.update { it.committed(CommittedTrip(flagged, 60.0, 1L)) }

        refresh(Unit).getOrThrow()

        assertFalse(trips.state.value.committed!!.plan.needsReplan)
    }

    @Test
    fun `a trip without stops fetches nothing`() = runBlocking {
        trips.update { it.planned(viaKassel.destination, viaKassel.copy(stops = emptyList())) }

        refresh(Unit).getOrThrow()

        assertTrue(sites.loaded.isEmpty())
    }

    private companion object {
        val kassel = ChargeSite(
            id = "bnetza:1141226",
            name = "Ionity Kassel",
            operator = "IONITY GmbH",
            position = LatLon(51.31, 9.49),
            connectors = listOf(Connector(ConnectorType.CCS2, 350.0, 6)),
        )
        val munich = Destination("München", LatLon(48.137, 11.575))
        val viaKassel = TripPlan(
            route = Route(listOf(LatLon(53.55, 9.99), kassel.position, munich.position), distanceKm = 776.0, durationMinutes = 470.0),
            destination = munich,
            stops = listOf(
                PlannedStop(
                    site = kassel.copy(connectors = emptyList()),
                    kmFromStart = 320.0,
                    arrivalSocPercent = 18.0,
                    departureSocPercent = 80.0,
                    chargeKwh = 48.0,
                    chargeMinutes = 24.0,
                    etaMinutesFromStart = 220.0,
                    maxPowerKw = 350.0,
                ),
            ),
            driveMinutes = 470.0,
            chargeMinutes = 24.0,
            arrivalSocPercent = 22.0,
        )
    }
}
