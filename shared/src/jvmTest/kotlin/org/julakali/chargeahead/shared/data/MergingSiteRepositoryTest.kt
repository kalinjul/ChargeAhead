package org.julakali.chargeahead.shared.data

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.flow.Flow

import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.NetworkPreferences

import org.julakali.chargeahead.shared.domain.BoundingBox

import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.destination
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MergingSiteRepositoryTest {

    private val location = LatLon(48.9331, 11.4779)
    private val area = SectorArea.circle(location, radiusKm = 40.0)

    private class FixedSiteRepository(private val sites: List<ChargeSite>) : SiteRepository {
        override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = flowOf(emptyList())
        override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = flowOf(emptyList())
        var queries = 0
            private set
        var invalidations = 0
            private set

        override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> {
            queries++
            return sites
        }

        override suspend fun invalidate() {
            invalidations++
        }
    }

    private class RecordingSiteRepository(private val sites: List<ChargeSite> = emptyList()) : SiteRepository {
        override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = flowOf(emptyList())
        override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = flowOf(emptyList())
        var recordedNetworks: Set<String>? = null
            private set

        override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> {
            recordedNetworks = networkKeys
            return sites
        }

        override suspend fun invalidate() {
        }
    }

    private class BrokenSiteRepository(private val reason: String = "No signal") : SiteRepository {
        override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = flowOf(emptyList())
        override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = flowOf(emptyList())
        override suspend fun invalidate() {}
        override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> =
            throw IllegalStateException(reason)
    }

    private fun site(id: String, offsetKm: Double = 0.0) = ChargeSite(
        id = id,
        name = "Ladepark $id",
        operator = "TestNetz",
        position = if (offsetKm == 0.0) location else location.destination(90.0, offsetKm),
        connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 2)),
        sources = setOf(id.substringBefore(":")),
    )

    @Test
    fun bothSourcesAreQueried() = runBlocking {
        val ocm = FixedSiteRepository(listOf(site("ocm:1", offsetKm = 5.0)))
        val bnetza = FixedSiteRepository(listOf(site("bnetza:2", offsetKm = 10.0)))

        val sites = MergingSiteRepository(listOf(ocm, bnetza)).load(area)

        assertEquals(1, ocm.queries)
        assertEquals(1, bnetza.queries)
        assertEquals(2, sites.size)
    }

    @Test
    fun theSameLocation_appearsOnlyOnce() = runBlocking {
        val sites = MergingSiteRepository(
            listOf(FixedSiteRepository(listOf(site("ocm:1"))), FixedSiteRepository(listOf(site("bnetza:2")))),
        ).load(area)

        assertEquals(1, sites.size)
        assertEquals(setOf("ocm", "bnetza"), sites.single().sources)
    }

    @Test
    fun ifOneSourceFails_theOthersStillCount() = runBlocking {
        // One failing source must not empty the list.
        val sites = MergingSiteRepository(
            listOf(FixedSiteRepository(listOf(site("ocm:1"))), BrokenSiteRepository()),
        ).load(area)

        assertEquals(listOf("ocm:1"), sites.map { it.id })
    }

    @Test
    fun ifTheOtherSourceFails_sameApplies() = runBlocking {
        val sites = MergingSiteRepository(
            listOf(BrokenSiteRepository(), FixedSiteRepository(listOf(site("bnetza:2")))),
        ).load(area)

        assertEquals(listOf("bnetza:2"), sites.map { it.id })
    }

    @Test
    fun ifAllSourcesFail_theErrorIsReported() {
        val repository = MergingSiteRepository(listOf(BrokenSiteRepository(), BrokenSiteRepository()))

        runBlocking {
            assertFailsWith<IllegalStateException> { repository.load(area) }
        }
    }

    @Test
    fun invalidate_reachesAllSources() = runBlocking {
        val ocm = FixedSiteRepository(emptyList())
        val bnetza = FixedSiteRepository(emptyList())

        MergingSiteRepository(listOf(ocm, bnetza)).invalidate()

        assertEquals(1, ocm.invalidations)
        assertEquals(1, bnetza.invalidations)
    }

    @Test
    fun withoutASource_thereIsNothingToMerge() {
        var thrown = false
        try {
            MergingSiteRepository(emptyList())
        } catch (expected: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun theMergerHandsTheSameNetworksToEverySource() = runBlocking {
        val source1 = RecordingSiteRepository()
        val source2 = RecordingSiteRepository()

        val networks = setOf("enbw")

        MergingSiteRepository(listOf(source1, source2)).load(area, networks)

        assertEquals(networks, source1.recordedNetworks)
        assertEquals(networks, source2.recordedNetworks)
    }

    private val FILTER = MapFilter(NetworkPreferences(), minPowerKw = 50.0, slowMode = false)

    /** The merge is the expensive step and the stores re-emit per tile written; none of that belongs on main. */
    @Test
    fun `stored sites are merged on the computation dispatcher`() = runBlocking<Unit> {
        var collectedOn: String? = null
        val source = object : SiteRepository {
            override suspend fun load(area: SearchArea, networkKeys: Set<String>) = emptyList<ChargeSite>()
            override suspend fun invalidate() {}
            override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> =
                flowOf(emptyList<ChargeSite>()).onEach { collectedOn = Thread.currentThread().name }
            override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = storedSitesIn(area.boundingBox, FILTER)
        }
        val repository = MergingSiteRepository(listOf(source), computation = Dispatchers.Default)

        repository.storedSitesIn(BoundingBox(0.0, 0.0, 1.0, 1.0), FILTER).first()

        assertTrue(collectedOn.orEmpty().startsWith("DefaultDispatcher-worker"), "collected on $collectedOn")
    }

    /** Each stock hands over its nearest; the merge of those is capped again, nearest first. */
    @Test
    fun `nearest sites are merged across stocks and capped`() = runBlocking {
        val here = LatLon(48.0, 11.0)
        fun stock(vararg sites: ChargeSite) = object : SiteRepository {
            override suspend fun load(area: SearchArea, networkKeys: Set<String>) = emptyList<ChargeSite>()
            override suspend fun invalidate() {}
            override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = flowOf(sites.toList())
            override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = flowOf(sites.toList())
        }
        fun site(id: String, northKm: Double) = ChargeSite(
            id = id, name = id, operator = "Ionity", position = LatLon(here.lat + northKm / 111.19, here.lon),
            connectors = listOf(Connector(ConnectorType.CCS2, 300.0, 4)),
        )
        val repository = MergingSiteRepository(
            listOf(stock(site("a3", 3.0), site("a1", 1.0)), stock(site("b2", 2.0), site("b4", 4.0))),
            computation = Dispatchers.Default,
        )

        val nearest = repository.storedSitesNearest(here, BoundingBox(47.0, 10.0, 49.0, 12.0), FILTER, limit = 3).first()

        assertEquals(listOf("a1", "b2", "a3"), nearest.map { it.id })
    }
}
