package de.autoapp.shared.data

import de.autoapp.shared.db.ChargeSiteDatabase
import de.autoapp.shared.db.DatabaseFactory
import de.autoapp.shared.db.createChargeSiteDatabase
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.ChargeSiteSource
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.TimeProvider
import de.autoapp.shared.domain.destination
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        // The actual advantage over an in-memory circle.
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
        // An empty list would be a lie here: it isn't known whether there's
        // no charging station there, or nobody has checked.
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
        // Otherwise an older app version would lose everything on a rollback.
        val decoded = "STECKER_AUS_DER_ZUKUNFT:150.0:2".decodeConnectors()

        assertEquals(1, decoded.size)
        assertEquals(ConnectorType.UNKNOWN, decoded.single().type)
        assertEquals(150.0, decoded.single().maxPowerKw)
    }

    @Test
    fun forACorridor_aFullCircleIsFetched() = runBlocking {
        // The heading turns while driving; a sector that rotated with it would
        // land partly outside the already-fetched area after every curve.
        val source = ControllableSource()
        val corridor = SectorArea(start, bearingDeg = 180.0, halfAngleDeg = 35.0, radiusKm = 40.0)

        TiledSiteRepository(source, database(), ControllableClock()).sitesIn(corridor)

        val fetched = requireNotNull(source.lastArea)
        assertTrue(fetched is SectorArea && fetched.halfAngleDeg >= 180.0, "Not a full circle")
        assertEquals(65.0, fetched.radiusKm)
    }

    @Test
    fun forARoute_theRouteBufferStaysUnchanged() = runBlocking {
        // The core point of the switch. If this got inflated into a circle
        // around the route's start, the whole route advantage would be gone:
        // a route's radiusKm is roughly its length.
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
}
