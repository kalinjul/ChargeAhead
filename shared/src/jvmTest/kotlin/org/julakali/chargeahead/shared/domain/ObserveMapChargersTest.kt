package org.julakali.chargeahead.shared.domain

import org.julakali.chargeahead.shared.settings.InMemoryKeyValueStorage
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ObserveMapChargersTest {

    private val viewport = BoundingBox(south = 51.0, west = 6.5, north = 51.4, east = 7.0)

    private val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
    private val fetchedAreas = mutableListOf<SearchArea>()
    private val fetchedNetworks = mutableListOf<List<Network>>()
    private val storedBoxes = mutableListOf<BoundingBox>()

    private fun site(id: String, operator: String, powerKw: Double, type: ConnectorType = ConnectorType.CCS2) =
        ChargeSite(
            id = "demo:$id",
            name = id,
            operator = operator,
            position = LatLon(51.2, 6.7),
            connectors = listOf(Connector(type, powerKw, 2)),
        )

    /** A store holding [stored]; a fetch adds [fetched] to it. */
    private fun observer(
        stored: List<ChargeSite>,
        fetched: List<ChargeSite> = emptyList(),
        configure: suspend PersistentSettingsStore.() -> Unit = {},
    ): ObserveMapChargers {
        runBlocking { settings.configure() }
        val store = stored.toMutableList()
        val repository = object : SiteRepository {
            override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> {
                fetchedAreas += area
                fetchedNetworks += networks
                store += fetched
                return fetched
            }

            override suspend fun storedSitesIn(box: BoundingBox): List<ChargeSite> {
                storedBoxes += box
                return store.toList()
            }
        }
        return ObserveMapChargers(repository, settings).also { it(ObserveMapChargers.Params(viewport)) }
    }

    private suspend fun ObserveMapChargers.await(matching: (MapChargers) -> Boolean = { true }): MapChargers =
        withTimeout(5_000) { flow.first(matching) }

    @Test
    fun `map chargers honor the physical filters`() = runBlocking<Unit> {
        val observe = observer(
            listOf(
                site("hpc", "Ionity", 350.0),
                site("slow-dc", "EnBW", 50.0),
                site("wallbox", "Stadtwerke", 22.0, ConnectorType.TYPE2),
            ),
        )

        // Default min power is 150 kW: the 50 kW DC and the AC post disappear.
        assertEquals(listOf("demo:hpc"), observe.await().chargers.map { it.site.id })
    }

    @Test
    fun `network filter applies on the map too`() = runBlocking<Unit> {
        val observe = observer(
            listOf(site("ionity", "Ionity", 350.0), site("fastned", "Fastned", 300.0)),
        ) {
            setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))
        }
        assertEquals(listOf("demo:fastned"), observe.await().chargers.map { it.site.id })
    }

    @Test
    fun `slow mode shows only sub-50kW chargers and ignores the fast filters`() = runBlocking<Unit> {
        val observe = observer(
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

        val ids = observe.await().chargers.map { it.site.id }.toSet()
        assertEquals(setOf("demo:wallbox", "demo:schuko"), ids)
    }

    @Test
    fun `a filter change selects again`() = runBlocking<Unit> {
        val observe = observer(listOf(site("hpc", "Ionity", 350.0), site("slow-dc", "EnBW", 50.0)))
        observe.await { it.chargers.size == 1 }

        settings.setChargeFilters(ChargeFilters(minPowerKw = 50.0))

        val result = observe.await { it.chargers.size == 2 }
        assertEquals(50.0, result.filter.minPowerKw)
    }

    @Test
    fun `without a viewport there are no chargers and no fetch`() = runBlocking<Unit> {
        val observe = observer(listOf(site("hpc", "Ionity", 350.0)))
        observe(ObserveMapChargers.Params(viewport = null))

        assertTrue(observe.await { it.chargers.isEmpty() }.chargers.isEmpty())
        assertTrue(fetchedAreas.isEmpty())
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
        val observe = observer(many) { setChargeFilters(ChargeFilters(minPowerKw = 50.0)) }

        val chargers = observe.await().chargers
        assertEquals(ObserveMapChargers.MAX_CHARGERS, chargers.size)
        assertEquals(
            (1..ObserveMapChargers.MAX_CHARGERS).map { "demo:s$it" },
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
        val observe = observer(listOf(outsideSite)) { setChargeFilters(ChargeFilters(minPowerKw = 50.0)) }

        assertNotNull(observe.await().chargers.find { it.site.id == "demo:outside" }, "Site in the pad must appear on the map")
    }

    @Test
    fun `fetched sites show up once the viewport is refilled`() = runBlocking<Unit> {
        val observe = observer(stored = emptyList(), fetched = listOf(site("new", "Ionity", 350.0)))

        assertEquals(listOf("demo:new"), observe.await { it.chargers.isNotEmpty() }.chargers.map { it.site.id })
    }

    @Test
    fun `fetch uses the strict viewport, the stored read gets the padded box`() = runBlocking<Unit> {
        val observe = observer(stored = emptyList(), fetched = listOf(site("new", "Ionity", 350.0)))
        observe.await { it.chargers.isNotEmpty() }

        assertEquals(viewport, (fetchedAreas.single() as ViewportArea).boundingBox)
        val padded = storedBoxes.first()
        assertTrue(padded.south < viewport.south, "padded box must extend south")
        assertTrue(padded.north > viewport.north, "padded box must extend north")
        assertTrue(padded.west < viewport.west, "padded box must extend west")
        assertTrue(padded.east > viewport.east, "padded box must extend east")
    }

    @Test
    fun `viewport fetch passes the network selection to the repository`() = runBlocking<Unit> {
        val observe = observer(stored = emptyList(), fetched = listOf(site("new", "Fastned", 300.0))) {
            setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))
        }
        observe.await { it.chargers.isNotEmpty() }

        assertTrue(
            fetchedNetworks.single().any { it.key == "fastned" },
            "Fastned must appear in the forwarded selection",
        )
    }

    @Test
    fun `slow mode fetches every network`() = runBlocking<Unit> {
        val observe = observer(
            stored = emptyList(),
            fetched = listOf(site("wallbox", "Stadtwerke", 22.0, ConnectorType.TYPE2)),
        ) {
            setChargeFilters(ChargeFilters(slowMode = true))
            setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))
        }
        observe.await { it.chargers.isNotEmpty() }

        assertEquals(listOf(emptyList()), fetchedNetworks)
    }
}
