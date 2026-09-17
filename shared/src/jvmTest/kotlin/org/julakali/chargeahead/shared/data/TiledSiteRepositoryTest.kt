package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeSiteSource
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.destination
import org.julakali.chargeahead.shared.domain.NetworkCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TiledSiteRepositoryTest {

    private val start = LatLon(48.9331, 11.4779)

    private class ControllableClock(var now: Long = 0L) : TimeProvider {
        override fun nowMillis() = now
    }

    private class ControllableSource(var sites: List<ChargeSite> = emptyList()) : ChargeSiteSource {
        override val id = "test"
        var queries = 0
            private set
        var broken = false

        var lastArea: SearchArea? = null
            private set

        override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            queries++
            lastArea = area
            if (broken) throw IllegalStateException("Dead zone")
            return sites
        }
    }

    private fun database(): ChargeSiteDatabase = createChargeSiteDatabase(DatabaseFactory())

    private fun site(id: String, bearingDeg: Double, distanceKm: Double) = ChargeSite(
        id = id,
        name = "Ladepark $id",
        operator = "TestNetz",
        position = start.destination(bearingDeg, distanceKm),
        connectors = listOf(Connector(ConnectorType.CCS2, maxPowerKw = 150.0, count = 4)),
    )

    private fun area(radiusKm: Double = 40.0) = SectorArea.circle(start, radiusKm)

    @Test
    fun firstQuery_goesToTheSourceAndReturnsResults() = runBlocking {
        val source = ControllableSource(listOf(site("a", 0.0, 10.0)))
        val repository = TiledSiteRepository(source, database(), ControllableClock())

        assertEquals(listOf("a"), repository.sitesIn(area()).map { it.id })
        assertEquals(1, source.queries)
    }

    @Test
    fun secondQuery_comesFromTheDatabase() = runBlocking {
        val source = ControllableSource(listOf(site("a", 0.0, 10.0)))
        val repository = TiledSiteRepository(source, database(), ControllableClock())

        repository.sitesIn(area())
        repository.sitesIn(area())

        assertEquals(1, source.queries)
    }

    @Test
    fun theLocalStoreSurvivesTheProcess() = runBlocking {
        val db = database()
        val firstSource = ControllableSource(listOf(site("a", 0.0, 10.0)))
        TiledSiteRepository(firstSource, db, ControllableClock()).sitesIn(area())

        // A new repository on the same database = an app restart.
        val secondSource = ControllableSource(emptyList())
        val afterRestart = TiledSiteRepository(secondSource, db, ControllableClock()).sitesIn(area())

        assertEquals(listOf("a"), afterRestart.map { it.id })
        assertEquals(0, secondSource.queries, "A fresh local store needs no network")
    }

    @Test
    fun inADeadZone_theLocalStoreStaysReadable() = runBlocking {
        val db = database()
        val source = ControllableSource(listOf(site("a", 0.0, 10.0)))
        TiledSiteRepository(source, db, ControllableClock()).sitesIn(area())

        // Drove on, new area, no network.
        source.broken = true
        val newArea = SectorArea.circle(start.destination(0.0, 5.0), radiusKm = 40.0)
        val list = TiledSiteRepository(source, db, ControllableClock()).sitesIn(newArea)

        assertEquals(listOf("a"), list.map { it.id }, "The list must not go empty in a tunnel")
    }

    @Test
    fun withNoStoreAndNoNetwork_theErrorIsReported() {
        // Nothing stored and no network: the failure must surface.
        val source = ControllableSource().apply { broken = true }
        val repository = TiledSiteRepository(source, database(), ControllableClock())

        runBlocking {
            assertFailsWith<IllegalStateException> { repository.sitesIn(area()) }
        }
    }

    @Test
    fun afterTheTtlExpires_itRefetches() = runBlocking {
        val clock = ControllableClock()
        val source = ControllableSource(listOf(site("a", 0.0, 10.0)))
        val repository = TiledSiteRepository(source, database(), clock, ttlMillis = 1_000L)

        repository.sitesIn(area())
        clock.now = 1_001L
        repository.sitesIn(area())

        assertEquals(2, source.queries)
    }

    @Test
    fun aNotYetVisitedArea_triggersAFetch() = runBlocking {
        val source = ControllableSource(listOf(site("a", 0.0, 10.0)))
        val repository = TiledSiteRepository(source, database(), ControllableClock())

        repository.sitesIn(area())
        repository.sitesIn(SectorArea.circle(LatLon(52.5, 13.4), radiusKm = 40.0))

        assertEquals(2, source.queries)
    }

    @Test
    fun invalidate_forcesARefetch_withoutClearingTheStore() = runBlocking {
        val source = ControllableSource(listOf(site("a", 0.0, 10.0)))
        val repository = TiledSiteRepository(source, database(), ControllableClock())
        repository.sitesIn(area())

        repository.invalidate()
        source.broken = true

        // Reloading fails, but the cache is still there.
        assertEquals(listOf("a"), repository.sitesIn(area()).map { it.id })
        assertEquals(2, source.queries)
    }

    @Test
    fun sitesOutsideTheArea_areNotReturned() = runBlocking {
        val db = database()
        val source = ControllableSource(listOf(site("nah", 0.0, 10.0), site("fern", 0.0, 300.0)))
        TiledSiteRepository(source, db, ControllableClock()).sitesIn(SectorArea.circle(start, 400.0))

        val smallArea = TiledSiteRepository(source, db, ControllableClock()).sitesIn(area(40.0))

        assertEquals(listOf("nah"), smallArea.map { it.id })
    }

    @Test
    fun aSiteIsUpdatedNotDuplicated() = runBlocking {
        val db = database()
        val clock = ControllableClock()
        val source = ControllableSource(listOf(site("a", 0.0, 10.0)))
        val repository = TiledSiteRepository(source, db, clock, ttlMillis = 1_000L)
        repository.sitesIn(area())

        source.sites = listOf(site("a", 0.0, 10.0).copy(name = "Renamed"))
        clock.now = 1_001L
        val list = repository.sitesIn(area())

        assertEquals(1, list.size)
        assertEquals("Renamed", list.single().name)
    }

    @Test
    fun connectorsSurviveBeingStored() = runBlocking {
        val db = database()
        val withConnectors = site("a", 0.0, 10.0).copy(
            connectors = listOf(
                Connector(ConnectorType.CCS2, maxPowerKw = 350.0, count = 6),
                Connector(ConnectorType.TYPE2, maxPowerKw = 22.0, count = null),
            ),
        )
        TiledSiteRepository(ControllableSource(listOf(withConnectors)), db, ControllableClock())
            .sitesIn(area())

        val loaded = TiledSiteRepository(ControllableSource(), db, ControllableClock())
            .sitesIn(area())
            .single()

        assertEquals(2, loaded.connectors.size)
        assertEquals(ConnectorType.CCS2, loaded.connectors[0].type)
        assertEquals(350.0, loaded.connectors[0].maxPowerKw)
        assertEquals(6, loaded.connectors[0].count)
        // An unknown count must stay unknown, not turn into 0.
        assertNull(loaded.connectors[1].count)
    }

    @Test
    fun aSiteWithoutConnectors_comesBackEmpty() = runBlocking {
        val db = database()
        val withoutConnectors = site("a", 0.0, 10.0).copy(connectors = emptyList())
        TiledSiteRepository(ControllableSource(listOf(withoutConnectors)), db, ControllableClock()).sitesIn(area())

        val loaded = TiledSiteRepository(ControllableSource(), db, ControllableClock())
            .sitesIn(area()).single()

        assertTrue(loaded.connectors.isEmpty())
    }

    @Test
    fun anUnknownConnectorTypeFromTheDatabase_doesNotCostTheWholeStore() {
        // Unknown enum names must not throw.
        val decoded = "STECKER_AUS_DER_ZUKUNFT:150.0:2".decodeConnectors()

        assertEquals(1, decoded.size)
        assertEquals(ConnectorType.UNKNOWN, decoded.single().type)
        assertEquals(150.0, decoded.single().maxPowerKw)
    }

    @Test
    fun storedSitesIn_returnsFromCacheWithoutQuerying() = runBlocking {
        // Populate the DB via a first repository, then prove a new one on the
        // same DB reads back via storedSitesIn without touching the source.
        val db = database()
        val populatingSource = ControllableSource(listOf(site("a", 0.0, 10.0)))
        TiledSiteRepository(populatingSource, db, ControllableClock()).sitesIn(area())

        val readingSource = ControllableSource(emptyList())
        val reader = TiledSiteRepository(readingSource, db, ControllableClock())
        val box = BoundingBox(south = 48.0, west = 10.0, north = 50.0, east = 13.0)
        val result = reader.storedSitesIn(box)

        assertEquals(listOf("a"), result.map { it.id })
        assertEquals(0, readingSource.queries, "storedSitesIn must not query the source")
    }

    @Test
    fun forACorridor_aFullCircleIsFetched() = runBlocking {
        // Sectors are fetched as full circles.
        val source = ControllableSource()
        val corridor = SectorArea(start, bearingDeg = 180.0, halfAngleDeg = 35.0, radiusKm = 40.0)

        TiledSiteRepository(source, database(), ControllableClock()).sitesIn(corridor)

        val fetched = requireNotNull(source.lastArea)
        assertTrue(fetched is SectorArea && fetched.halfAngleDeg >= 180.0, "Not a full circle")
        assertEquals(65.0, fetched.radiusKm)
    }

    private class RecordingSource(override val id: String = "ocm") : ChargeSiteSource {
        val calls = mutableListOf<List<Network>>()
        var toReturn: List<ChargeSite> = emptyList()
        override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            calls += networks; return toReturn
        }
    }

    @Test
    fun adding_a_network_fetches_only_the_missing_one() = runTest {
        val src = RecordingSource()
        val repo = TiledSiteRepository(src, database(), ControllableClock(1000L))
        val enbw = NetworkCatalog.byKey("enbw")!!
        val ionity = NetworkCatalog.byKey("ionity")!!
        val a = SectorArea.circle(LatLon(48.1, 11.5), 1.0)

        repo.sitesIn(a, listOf(enbw))
        repo.sitesIn(a, listOf(enbw, ionity))

        assertEquals(2, src.calls.size)
        assertEquals(listOf("enbw"), src.calls[0].map { it.key })
        assertEquals(listOf("ionity"), src.calls[1].map { it.key })
    }

    @Test
    fun removing_a_network_fetches_nothing() = runTest {
        val src = RecordingSource()
        val repo = TiledSiteRepository(src, database(), ControllableClock(1000L))
        val enbw = NetworkCatalog.byKey("enbw")!!
        val ionity = NetworkCatalog.byKey("ionity")!!
        val a = SectorArea.circle(LatLon(48.1, 11.5), 1.0)

        repo.sitesIn(a, listOf(enbw, ionity))
        val before = src.calls.size
        repo.sitesIn(a, listOf(enbw))

        assertEquals(before, src.calls.size)
    }

    @Test
    fun unfiltered_uses_sentinel_and_behaves_as_before() = runTest {
        val src = RecordingSource()
        val repo = TiledSiteRepository(src, database(), ControllableClock(1000L))
        val a = SectorArea.circle(LatLon(48.1, 11.5), 1.0)

        repo.sitesIn(a)
        repo.sitesIn(a)

        assertEquals(1, src.calls.size)
        assertEquals(emptyList(), src.calls[0])
    }

    @Test
    fun forARoute_theRouteBufferStaysUnchanged() = runBlocking {
        // A route must not be inflated into a circle around its start.
        val source = ControllableSource()
        val route = PolylineArea(
            listOf(start, start.destination(180.0, 80.0), start.destination(180.0, 170.0)),
            bufferKm = 2.0,
        )

        TiledSiteRepository(source, database(), ControllableClock()).sitesIn(route)

        val fetched = requireNotNull(source.lastArea)
        assertTrue(fetched is PolylineArea, "The route buffer turned into ${fetched::class.simpleName}")
        assertEquals(2.0, fetched.bufferKm)
    }

    @Test
    fun whileContinuingOnTheRoute_noRefetchHappens() = runBlocking {
        // aheadOf() only shrinks the area — whatever was fetched once covers
        // the rest of the trip.
        val source = ControllableSource(listOf(site("a", 180.0, 10.0)))
        val repository = TiledSiteRepository(source, database(), ControllableClock())
        val route = PolylineArea(
            listOf(start, start.destination(180.0, 80.0), start.destination(180.0, 170.0)),
            bufferKm = 2.0,
        )

        repository.sitesIn(route)
        repository.sitesIn(requireNotNull(route.aheadOf(start.destination(180.0, 40.0))))

        assertEquals(1, source.queries)
    }

    @Test
    fun aRunningFetch_isReportedUntilItEnds_butACacheHitIsNot() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val source = object : ChargeSiteSource {
            override val id = "test"
            override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> {
                gate.await()
                return listOf(site("a", 0.0, 10.0))
            }
        }
        val activity = SiteFetchActivity()
        val repository = TiledSiteRepository(source, database(), ControllableClock(), fetchActivity = activity)

        val fetch = async { repository.sitesIn(area()) }
        withTimeout(5_000) { activity.isFetching.first { it } }
        gate.complete(Unit)
        fetch.await()
        assertFalse(activity.isFetching.first())

        // Covered now: answered from the store, nothing to report.
        val seen = mutableListOf<Boolean>()
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) { activity.isFetching.collect { seen += it } }
        repository.sitesIn(area())
        watcher.cancel()
        assertEquals(listOf(false), seen)
    }

    @Test
    fun aFailedFetch_stillClearsTheActivity() = runBlocking {
        val source = ControllableSource().apply { broken = true }
        val activity = SiteFetchActivity()
        val repository = TiledSiteRepository(source, database(), ControllableClock(), fetchActivity = activity)

        assertFailsWith<IllegalStateException> { repository.sitesIn(area()) }
        assertFalse(activity.isFetching.first())
    }

    @Test
    fun aStalledFetch_doesNotBlockFetchesForOtherAreas() = runBlocking<Unit> {
        // Area A's fetch hangs; a fetch for area B must still complete.
        val gate = CompletableDeferred<Unit>()
        val source = object : ChargeSiteSource {
            override val id = "test"
            var queries = 0
            override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> {
                queries++
                if (queries == 1) gate.await() // only the first area stalls
                return emptyList()
            }
        }
        val repository = TiledSiteRepository(source, database(), ControllableClock())

        val stalled = async { repository.sitesIn(area()) }
        while (source.queries < 1) yield() // let area A reach its gated query

        // Would time out if it queued behind the stalled fetch.
        withTimeout(5_000) { repository.sitesIn(SectorArea.circle(LatLon(52.5, 13.4), 40.0)) }

        assertTrue(stalled.isActive, "area A's fetch is still stalled")
        gate.complete(Unit)
        stalled.await()
    }

    @Test
    fun concurrentFetchesForTheSameArea_hitTheSourceOnce() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val source = object : ChargeSiteSource {
            override val id = "test"
            var queries = 0
            override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> {
                queries++
                gate.await()
                return listOf(site("a", 0.0, 10.0))
            }
        }
        val repository = TiledSiteRepository(source, database(), ControllableClock())
        val a = area()

        val first = async { repository.sitesIn(a) }
        while (source.queries < 1) yield() // first fetch is in flight
        val second = async { repository.sitesIn(a) }
        yield() // let the second reach the de-dup
        gate.complete(Unit)

        assertEquals(listOf("a"), first.await().map { it.id })
        assertEquals(listOf("a"), second.await().map { it.id })
        assertEquals(1, source.queries, "the identical concurrent fetch was de-duplicated")
    }

    /** Answers like the real sources do: only what lies inside the requested shape. */
    private class ShapeClippingSource(private val sites: List<ChargeSite>) : ChargeSiteSource {
        override val id = "test"
        var queries = 0
            private set

        override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            queries++
            return sites.filter { it.position in area }
        }
    }

    private fun siteAt(id: String, position: LatLon) = ChargeSite(
        id = id,
        name = "Ladepark $id",
        operator = "TestNetz",
        position = position,
        connectors = listOf(Connector(ConnectorType.CCS2, maxPowerKw = 150.0, count = 4)),
    )

    @Test
    fun afterARoute_aSiteOffTheCorridorButInsideItsBox_isStillFound() = runBlocking {
        // A diagonal route: its box is ~57 × 57 km, the corridor only ~6 km
        // wide. The site sits ~28 km off the route, inside the box — the
        // route fetch never asked for it, so the area around it is unchecked.
        val offCorridor = start.destination(90.0, 40.0).destination(180.0, 10.0)
        val source = ShapeClippingSource(listOf(siteAt("off", offCorridor)))
        val repository = TiledSiteRepository(source, database(), ControllableClock())
        val route = PolylineArea(listOf(start, start.destination(135.0, 80.0)), bufferKm = 3.0)

        repository.sitesIn(route)
        val nearby = repository.sitesIn(SectorArea.circle(offCorridor, 3.0))

        assertEquals(listOf("off"), nearby.map { it.id }, "The route's box was recorded as checked")
    }

    @Test
    fun afterANarrowRouteBuffer_aWiderBufferOnTheSameRoute_findsTheSitesInBetween() = runBlocking {
        // The car list follows the route with 2 km, the trip planner with 3 km.
        // A site 2.5 km off the route belongs to the second answer.
        val route = listOf(start, start.destination(180.0, 80.0), start.destination(180.0, 170.0))
        val between = start.destination(180.0, 40.0).destination(90.0, 2.5)
        val source = ShapeClippingSource(listOf(siteAt("between", between)))
        val repository = TiledSiteRepository(source, database(), ControllableClock())

        repository.sitesIn(PolylineArea(route, bufferKm = 2.0))
        val wider = PolylineArea(route, bufferKm = 3.0)
        val found = repository.sitesIn(wider).filter { it.position in wider }

        assertEquals(listOf("between"), found.map { it.id }, "The 2 km fetch was recorded as covering the 3 km buffer")
    }
}
