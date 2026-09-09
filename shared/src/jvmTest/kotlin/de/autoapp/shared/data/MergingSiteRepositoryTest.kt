package de.autoapp.shared.data

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.NetworkCatalog
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.destination
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MergingSiteRepositoryTest {

    private val location = LatLon(48.9331, 11.4779)
    private val area = SectorArea.circle(location, radiusKm = 40.0)

    private class FixedSiteRepository(private val sites: List<ChargeSite>) : SiteRepository {
        var queries = 0
            private set
        var invalidations = 0
            private set

        override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            queries++
            return sites
        }

        override suspend fun invalidate() {
            invalidations++
        }
    }

    private class RecordingSiteRepository(private val sites: List<ChargeSite> = emptyList()) : SiteRepository {
        var recordedNetworks: List<Network>? = null
            private set

        override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            recordedNetworks = networks
            return sites
        }

        override suspend fun invalidate() {
        }
    }

    private class BrokenSiteRepository(private val reason: String = "No signal") : SiteRepository {
        override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> =
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

        val sites = MergingSiteRepository(listOf(ocm, bnetza)).sitesIn(area)

        assertEquals(1, ocm.queries)
        assertEquals(1, bnetza.queries)
        assertEquals(2, sites.size)
    }

    @Test
    fun theSameLocation_appearsOnlyOnce() = runBlocking {
        val sites = MergingSiteRepository(
            listOf(FixedSiteRepository(listOf(site("ocm:1"))), FixedSiteRepository(listOf(site("bnetza:2")))),
        ).sitesIn(area)

        assertEquals(1, sites.size)
        assertEquals(setOf("ocm", "bnetza"), sites.single().sources)
    }

    @Test
    fun ifOneSourceFails_theOthersStillCount() = runBlocking {
        // The register going down must not empty the list when OpenChargeMap
        // still responds.
        val sites = MergingSiteRepository(
            listOf(FixedSiteRepository(listOf(site("ocm:1"))), BrokenSiteRepository()),
        ).sitesIn(area)

        assertEquals(listOf("ocm:1"), sites.map { it.id })
    }

    @Test
    fun ifTheOtherSourceFails_sameApplies() = runBlocking {
        val sites = MergingSiteRepository(
            listOf(BrokenSiteRepository(), FixedSiteRepository(listOf(site("bnetza:2")))),
        ).sitesIn(area)

        assertEquals(listOf("bnetza:2"), sites.map { it.id })
    }

    @Test
    fun ifAllSourcesFail_theErrorIsReported() {
        val repository = MergingSiteRepository(listOf(BrokenSiteRepository(), BrokenSiteRepository()))

        runBlocking {
            assertFailsWith<IllegalStateException> { repository.sitesIn(area) }
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

        // Get test data: enbw network from catalog
        val enbw = NetworkCatalog.byKey("enbw")!!
        val networks = listOf(enbw)

        MergingSiteRepository(listOf(source1, source2)).sitesIn(area, networks)

        assertEquals(networks, source1.recordedNetworks)
        assertEquals(networks, source2.recordedNetworks)
    }
}
