package de.autoapp.shared

import de.autoapp.shared.core.TripPlanner
import de.autoapp.shared.data.DemoTariffSource
import de.autoapp.shared.domain.BoundingBox
import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.Route
import de.autoapp.shared.domain.RouteEngine
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.ViewportArea
import de.autoapp.shared.settings.InMemoryKeyValueStorage
import de.autoapp.shared.settings.PersistentSettingsStore
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
        val tariffs = DemoTariffSource()
        return PlanningFeature(TripPlanner(engine, repository, tariffs), repository, tariffs, settings)
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
    fun `viewport fetch passes the network selection to the repository`() = runBlocking<Unit> {
        val capturedNetworks = mutableListOf<List<Network>>()
        val settings = de.autoapp.shared.settings.PersistentSettingsStore(de.autoapp.shared.settings.InMemoryKeyValueStorage())
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
        val tariffs = DemoTariffSource()
        val feature = PlanningFeature(de.autoapp.shared.core.TripPlanner(engine, repository, tariffs), repository, tariffs, settings)

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
    fun `the cap keeps the strongest sites`() = runBlocking<Unit> {
        val many = (1..350).map { site("s$it", "EnBW", 150.0 + it) }
        val feature = featureWith(many) { setChargeFilters(ChargeFilters(minPowerKw = 50.0)) }

        val chargers = feature.chargersIn(viewport)
        assertEquals(PlanningFeature.MAX_MAP_CHARGERS, chargers.size)
        assertTrue(chargers.first().maxPowerKw >= chargers.last().maxPowerKw)
        assertEquals(500.0, chargers.first().maxPowerKw)
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
        val fetchedAreas = mutableListOf<de.autoapp.shared.domain.SearchArea>()
        val storedBoxes = mutableListOf<BoundingBox>()

        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val repository = object : SiteRepository {
            override suspend fun sitesIn(area: de.autoapp.shared.domain.SearchArea, networks: List<Network>): List<ChargeSite> {
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
        val tariffs = DemoTariffSource()
        val feature = PlanningFeature(TripPlanner(engine, repository, tariffs), repository, tariffs, settings)

        feature.chargersIn(viewport)

        // fetch is the exact viewport
        assertTrue(fetchedAreas.isNotEmpty(), "sitesIn must have been called")
        val fetchBox = (fetchedAreas.first() as de.autoapp.shared.domain.ViewportArea).boundingBox
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
