package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.settings.InMemoryKeyValueStorage
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The chain destination -> route -> route buffer, and the fallback to the corridor.
 */
class ChargeStopsFeatureRouteTest {

    private val nuremberg = LatLon(49.4521, 11.0767)
    private val munich = Destination("München Hbf", LatLon(48.1351, 11.5820))

    private val a9 = listOf(
        LatLon(49.4521, 11.0767),
        LatLon(49.19, 11.19),
        LatLon(48.93, 11.46),
        LatLon(48.55, 11.52),
        LatLon(48.1351, 11.5820),
    )

    private class ControllableLocationSource : LocationSource {
        val fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 8)
        override val updates: Flow<Fix> = fixes
    }

    private class RecordingSiteRepository : SiteRepository {
        var lastArea: SearchArea? = null
            private set

        override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            lastArea = area
            return emptyList()
        }
    }

    private class FixedRoute(private val route: Route?) : RouteEngine {
        var calls = 0
            private set

        override suspend fun route(from: LatLon, to: LatLon): Route? {
            calls++
            return route
        }
    }

    private class BrokenRouter : RouteEngine {
        override suspend fun route(from: LatLon, to: LatLon): Route? =
            throw IllegalStateException("Funkloch")
    }

    private fun fix(position: LatLon = nuremberg, timestampMillis: Long = 0L) =
        Fix(position, bearingDeg = 180.0, speedMps = 30.0, timestampMillis = timestampMillis)

    private fun feature(
        locationSource: LocationSource,
        repository: SiteRepository,
        settingsStore: PersistentSettingsStore,
        routeEngine: RouteEngine? = FixedRoute(Route(a9, 170.0, 108.0)),
        geocoder: Geocoder? = null,
    ) = ChargeStopsFeature(
        locationSource = locationSource,
        repository = repository,
        settingsStore = settingsStore,
        routeEngine = routeEngine,
        geocoder = geocoder,
        dispatcher = Dispatchers.Unconfined,
    )

    private fun settingsStore() = PersistentSettingsStore(InMemoryKeyValueStorage())

    @Test
    fun withoutDestination_searchesInDrivingDirection() = runBlocking {
        val location = ControllableLocationSource()
        val repository = RecordingSiteRepository()
        val feature = feature(location, repository, settingsStore())
        feature.start()

        location.fixes.emit(fix())

        assertIs<SectorArea>(repository.lastArea)
        assertEquals(ChargeStopsState.RouteStatus.NONE, feature.state.value.routeStatus)

        feature.close()
    }

    @Test
    fun withDestination_searchesAlongTheRoute() = runBlocking {
        val location = ControllableLocationSource()
        val repository = RecordingSiteRepository()
        val settingsStore = settingsStore()
        val feature = feature(location, repository, settingsStore)
        feature.start()
        location.fixes.emit(fix())

        feature.setDestination(munich)

        val area = assertIs<PolylineArea>(repository.lastArea)
        assertEquals(2.0, area.bufferKm)
        assertEquals(ChargeStopsState.RouteStatus.ACTIVE, feature.state.value.routeStatus)
        assertEquals(munich, feature.state.value.destination)

        feature.close()
    }

    @Test
    fun theRouteIsComputedOnlyOnce() = runBlocking {
        // The route stays; only the section ahead shrinks.
        val location = ControllableLocationSource()
        val router = FixedRoute(Route(a9, 170.0, 108.0))
        val feature = feature(location, RecordingSiteRepository(), settingsStore(), routeEngine = router)
        feature.start()
        location.fixes.emit(fix(timestampMillis = 0L))
        feature.setDestination(munich)

        location.fixes.emit(fix(a9[1], timestampMillis = 100_000L))
        location.fixes.emit(fix(a9[2], timestampMillis = 200_000L))
        location.fixes.emit(fix(a9[3], timestampMillis = 300_000L))

        assertEquals(1, router.calls, "The route was computed more than once")

        feature.close()
    }

    @Test
    fun theSearchAreaShrinksWhileDriving() = runBlocking {
        val location = ControllableLocationSource()
        val repository = RecordingSiteRepository()
        val feature = feature(location, repository, settingsStore())
        feature.start()
        location.fixes.emit(fix(timestampMillis = 0L))
        feature.setDestination(munich)
        val atStart = requireNotNull(repository.lastArea).radiusKm

        location.fixes.emit(fix(a9[3], timestampMillis = 300_000L))

        val later = requireNotNull(repository.lastArea).radiusKm
        assertTrue(later < atStart, "$later was not smaller than $atStart")

        feature.close()
    }

    @Test
    fun clearingDestination_switchesBackToDrivingDirection() = runBlocking {
        val location = ControllableLocationSource()
        val repository = RecordingSiteRepository()
        val feature = feature(location, repository, settingsStore())
        feature.start()
        location.fixes.emit(fix())
        feature.setDestination(munich)
        assertIs<PolylineArea>(repository.lastArea)

        feature.setDestination(null)

        assertIs<SectorArea>(repository.lastArea)
        assertEquals(ChargeStopsState.RouteStatus.NONE, feature.state.value.routeStatus)
        assertEquals(null, feature.state.value.destination)

        feature.close()
    }

    @Test
    fun whenRouteComputationFails_theCorridorRemains() = runBlocking {
        // The list stays, but the status says the route is unavailable.
        val location = ControllableLocationSource()
        val repository = RecordingSiteRepository()
        val feature = feature(location, repository, settingsStore(), routeEngine = BrokenRouter())
        feature.start()
        location.fixes.emit(fix())

        feature.setDestination(munich)

        assertIs<SectorArea>(repository.lastArea)
        assertEquals(ChargeStopsState.RouteStatus.UNAVAILABLE, feature.state.value.routeStatus)
        assertEquals(munich, feature.state.value.destination)

        feature.close()
    }

    @Test
    fun withoutRoadConnection_theCorridorRemains() = runBlocking {
        val location = ControllableLocationSource()
        val repository = RecordingSiteRepository()
        val feature = feature(location, repository, settingsStore(), routeEngine = FixedRoute(null))
        feature.start()
        location.fixes.emit(fix())

        feature.setDestination(munich)

        assertIs<SectorArea>(repository.lastArea)
        assertEquals(ChargeStopsState.RouteStatus.UNAVAILABLE, feature.state.value.routeStatus)

        feature.close()
    }

    @Test
    fun destinationSetBeforeFirstFix_isAppliedLater() = runBlocking {
        // Routing only works once it's known where the trip starts.
        val location = ControllableLocationSource()
        val repository = RecordingSiteRepository()
        val settingsStore = settingsStore()
        val feature = feature(location, repository, settingsStore)
        feature.start()

        feature.setDestination(munich)
        assertEquals(ChargeStopsState.RouteStatus.UNAVAILABLE, feature.state.value.routeStatus)

        location.fixes.emit(fix())

        assertIs<PolylineArea>(repository.lastArea)
        assertEquals(ChargeStopsState.RouteStatus.ACTIVE, feature.state.value.routeStatus)

        feature.close()
    }

    @Test
    fun aSavedDestination_persistsAcrossRestart() = runBlocking {
        val storage = InMemoryKeyValueStorage()
        PersistentSettingsStore(storage).setDestination(munich)

        val location = ControllableLocationSource()
        val repository = RecordingSiteRepository()
        val feature = feature(location, repository, PersistentSettingsStore(storage))
        feature.start()
        location.fixes.emit(fix())

        assertIs<PolylineArea>(repository.lastArea)

        feature.close()
    }

    @Test
    fun withoutGeocoder_searchReturnsAnEmptyList() = runBlocking {
        val feature = feature(ControllableLocationSource(), RecordingSiteRepository(), settingsStore(), geocoder = null)

        assertTrue(feature.searchDestinations("München").isEmpty())

        feature.close()
    }

    @Test
    fun theDestinationSearchPassesTheLocationAlong() = runBlocking {
        // "Hauptbahnhof" (main station) is ambiguous without a nearby location.
        var seen: LatLon? = null
        val geocoder = object : Geocoder {
            override suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> {
                seen = near
                return listOf(Place("Treffer", "Treffer", LatLon(48.0, 11.0)))
            }
        }
        val location = ControllableLocationSource()
        val feature = feature(location, RecordingSiteRepository(), settingsStore(), geocoder = geocoder)
        feature.start()
        location.fixes.emit(fix())

        feature.searchDestinations("Hauptbahnhof")

        assertEquals(nuremberg, seen)

        feature.close()
    }
}
