package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.core.TripPlanner
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.ViewportArea
import org.julakali.chargeahead.shared.settings.InMemoryKeyValueStorage
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlanningFeatureViewportTest {

    private val viewport = BoundingBox(south = 51.0, west = 6.5, north = 51.4, east = 7.0)

    private fun site(id: String, operator: String, powerKw: Double, type: ConnectorType = ConnectorType.CCS2) =
        ChargeSite(
            id = "demo:$id",
            name = id,
            operator = operator,
            position = LatLon(51.2, 6.7),
            connectors = listOf(Connector(type, powerKw, 2)),
        )

    private fun featureWith(sites: List<ChargeSite>, configure: suspend PersistentSettingsStore.() -> Unit = {}): PlanningFeature {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        runBlocking { settings.configure() }
        val repository = object : SiteRepository {
            override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> = sites
            override suspend fun storedSitesIn(box: BoundingBox): List<ChargeSite> = sites
        }
        val engine = object : RouteEngine {
            override suspend fun route(from: LatLon, to: LatLon): Route? = null
        }
        return PlanningFeature(TripPlanner(engine, repository), repository, settings)
    }

    @Test
    fun `map chargers honor the physical filters`() = runBlocking<Unit> {
        val feature = featureWith(
            listOf(
                site("hpc", "Ionity", 350.0),
                site("slow-dc", "EnBW", 50.0),
                site("wallbox", "Stadtwerke", 22.0, ConnectorType.TYPE2),
            ),
        )
        val chargers = feature.chargersIn(viewport)

        // Default min power is 150 kW: the 50 kW DC and the AC post disappear.
        assertEquals(listOf("demo:hpc"), chargers.map { it.site.id })
    }

    @Test
    fun `network filter applies on the map too`() = runBlocking<Unit> {
        val feature = featureWith(
            listOf(site("ionity", "Ionity", 350.0), site("fastned", "Fastned", 300.0)),
        ) {
            setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))
        }
        assertEquals(listOf("demo:fastned"), feature.chargersIn(viewport).map { it.site.id })
    }

    @Test
    fun `slow mode shows only sub-50kW chargers and ignores the fast filters`() = runBlocking<Unit> {
        val feature = featureWith(
            listOf(
                site("hpc", "Ionity", 350.0),
                site("wallbox", "Stadtwerke", 22.0, ConnectorType.TYPE2),
                site("schuko", "Hotel", 3.7, ConnectorType.SCHUKO),
            ),
        ) {
            // Filters that would normally hide the AC posts and everything but
            // Ionity — slow mode drops both.
            setChargeFilters(ChargeFilters(minPowerKw = 150.0, slowMode = true))
            setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity")))
        }

        val ids = feature.chargersIn(viewport).map { it.site.id }.toSet()
        assertEquals(setOf("demo:wallbox", "demo:schuko"), ids)
    }

    @Test
    fun `viewport fetch passes the network selection to the repository`() = runBlocking<Unit> {
        val capturedNetworks = mutableListOf<List<Network>>()
        val settings = org.julakali.chargeahead.shared.settings.PersistentSettingsStore(org.julakali.chargeahead.shared.settings.InMemoryKeyValueStorage())
        runBlocking {
            settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))
        }
        val repository = object : SiteRepository {
            override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> {
                capturedNetworks += networks
                return emptyList()
            }
        }
        val engine = object : RouteEngine {
            override suspend fun route(from: LatLon, to: LatLon): Route? = null
        }
        val feature = PlanningFeature(org.julakali.chargeahead.shared.core.TripPlanner(engine, repository), repository, settings)

        feature.chargersIn(viewport)

        assertTrue(capturedNetworks.isNotEmpty(), "repository must have been queried")
        assertTrue(
            capturedNetworks.all { it.isNotEmpty() },
            "the viewport fetch must forward the active network selection",
        )
        assertTrue(
            capturedNetworks.all { networks -> networks.any { it.key == "fastned" } },
            "Fastned must appear in the forwarded selection",
        )
    }

    @Test
    fun `the cap keeps the nearest sites, dropping the far ones`() = runBlocking<Unit> {
        // 350 sites marching away from the viewport centre; only the nearest fit.
        val centreLat = (viewport.south + viewport.north) / 2.0
        val centreLon = (viewport.west + viewport.east) / 2.0
        val many = (1..350).map { i ->
            ChargeSite(
                id = "demo:s$i",
                name = "s$i",
                operator = "EnBW",
                position = LatLon(centreLat + i * 0.001, centreLon),
                connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 2)),
            )
        }
        val feature = featureWith(many) { setChargeFilters(ChargeFilters(minPowerKw = 50.0)) }

        val chargers = feature.chargersIn(viewport)
        assertEquals(PlanningFeature.MAX_MAP_CHARGERS, chargers.size)
        assertEquals(
            (1..PlanningFeature.MAX_MAP_CHARGERS).map { "demo:s$it" },
            chargers.map { it.site.id },
            "the nearest sites survive, the far ones are dropped",
        )
    }

    @Test
    fun `site outside the viewport but inside the 2-screen pad is included`() = runBlocking<Unit> {
        // viewport: south=51.0, north=51.4 — height 0.4°, padLat = 0.8°
        // padded north = 52.2; site at 51.5 is outside viewport but inside pad
        val outsideSite = ChargeSite(
            id = "demo:outside",
            name = "outside",
            operator = "EnBW",
            position = LatLon(51.5, 6.7),
            connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 2)),
        )
        val feature = featureWith(listOf(outsideSite)) {
            setChargeFilters(ChargeFilters(minPowerKw = 50.0))
        }

        val chargers = feature.chargersIn(viewport)
        assertNotNull(chargers.find { it.site.id == "demo:outside" }, "Site in the pad must appear on the map")
    }

    @Test
    fun `fetch uses the strict viewport, storedSitesIn gets the padded box`() = runBlocking<Unit> {
        val sitesInStore = listOf(site("cached", "EnBW", 150.0))
        val fetchedAreas = mutableListOf<org.julakali.chargeahead.shared.domain.SearchArea>()
        val storedBoxes = mutableListOf<BoundingBox>()

        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val repository = object : SiteRepository {
            override suspend fun sitesIn(area: org.julakali.chargeahead.shared.domain.SearchArea, networks: List<Network>): List<ChargeSite> {
                fetchedAreas += area
                return emptyList()
            }
            override suspend fun storedSitesIn(box: BoundingBox): List<ChargeSite> {
                storedBoxes += box
                return sitesInStore
            }
        }
        val engine = object : RouteEngine {
            override suspend fun route(from: LatLon, to: LatLon): Route? = null
        }
        val feature = PlanningFeature(TripPlanner(engine, repository), repository, settings)

        feature.chargersIn(viewport)

        // fetch is the exact viewport
        assertTrue(fetchedAreas.isNotEmpty(), "sitesIn must have been called")
        val fetchBox = (fetchedAreas.first() as org.julakali.chargeahead.shared.domain.ViewportArea).boundingBox
        assertEquals(viewport, fetchBox, "fetch must use the strict viewport")

        // stored read is bigger
        assertTrue(storedBoxes.isNotEmpty(), "storedSitesIn must have been called")
        val padded = storedBoxes.first()
        assertTrue(padded.south < viewport.south, "padded box must extend south")
        assertTrue(padded.north > viewport.north, "padded box must extend north")
        assertTrue(padded.west < viewport.west, "padded box must extend west")
        assertTrue(padded.east > viewport.east, "padded box must extend east")
    }
}
