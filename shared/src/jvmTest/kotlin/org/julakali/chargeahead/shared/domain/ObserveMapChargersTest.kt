package org.julakali.chargeahead.shared.domain

import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserveMapChargersTest {

    private val viewport = BoundingBox(south = 51.0, west = 6.5, north = 51.4, east = 7.0)

    private val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
    private val fetchedAreas = mutableListOf<SearchArea>()
    private val storedBoxes = mutableListOf<BoundingBox>()
    private val storedFilters = mutableListOf<MapFilter>()
    private lateinit var repository: SiteRepository
    private val statusStore = MutableStateFlow<Map<String, List<ChargePointStatus>>>(emptyMap())
    private val refreshedIds = mutableListOf<Collection<String>>()
    private val statusRepository = object : ChargePointStatusRepository {
        override val statuses = statusStore

        override suspend fun refresh(ids: Collection<String>) {
            refreshedIds += ids
        }
    }

    private fun site(
        id: String,
        operator: String,
        powerKw: Double,
        type: ConnectorType = ConnectorType.CCS2,
        liveStatusId: String? = null,
    ) = ChargeSite(
        id = "demo:$id",
        name = id,
        operator = operator,
        position = LatLon(51.2, 6.7),
        connectors = listOf(Connector(type, powerKw, 2)),
        liveStatusId = liveStatusId,
    )

    /** A store holding [stored], which leaves filtering to the real store; a fetch adds [fetched] to it. */
    private fun observer(
        stored: List<ChargeSite>,
        fetched: List<ChargeSite> = emptyList(),
        configure: suspend PersistentSettingsStore.() -> Unit = {},
    ): ObserveMapChargers {
        runBlocking { settings.configure() }
        val store = MutableStateFlow(stored)
        repository = object : SiteRepository {
            override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> {
                fetchedAreas += area
                store.update { it + fetched }
                return fetched
            }

            override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> {
                storedBoxes += box
                storedFilters += filter
                return store
            }
        }
        return ObserveMapChargers(repository, statusRepository, settings).also { it(ObserveMapChargers.Params(viewport)) }
    }

    private suspend fun ObserveMapChargers.await(matching: (MapChargers) -> Boolean = { true }): MapChargers =
        withTimeout(5_000) { flow.first(matching) }

    @Test
    fun `the store is asked with the driver's filter`() = runBlocking<Unit> {
        val observe = observer(listOf(site("fastned", "Fastned", 300.0))) {
            setChargeFilters(ChargeFilters(minPowerKw = 100.0))
            setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))
        }

        val result = observe.await()

        val expected = MapFilter(
            networks = NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")),
            minPowerKw = 100.0,
            slowMode = false,
        )
        assertEquals(expected, storedFilters.single())
        assertEquals(expected, result.filter)
        assertEquals(300.0, result.chargers.single().maxPowerKw)
    }

    @Test
    fun `a filter change asks the store again`() = runBlocking<Unit> {
        val observe = observer(listOf(site("hpc", "Ionity", 350.0)))
        observe.await()

        settings.setChargeFilters(ChargeFilters(minPowerKw = 50.0))

        assertEquals(50.0, observe.await { it.filter.minPowerKw == 50.0 }.filter.minPowerKw)
        assertEquals(listOf(ChargeFilters().minPowerKw, 50.0), storedFilters.map { it.minPowerKw })
    }

    @Test
    fun `in slow mode a charger shows its strongest connector of any type`() = runBlocking<Unit> {
        val wallbox = site("wallbox", "Stadtwerke", 22.0, ConnectorType.TYPE2)
        val observe = observer(listOf(wallbox)) { setChargeFilters(ChargeFilters(slowMode = true)) }

        assertEquals(listOf(MapCharger(wallbox, 22.0)), observe.await().chargers)
    }

    @Test
    fun `outside slow mode a site without a DC connector is not a map charger`() = runBlocking<Unit> {
        val observe = observer(listOf(site("wallbox", "Stadtwerke", 22.0, ConnectorType.TYPE2)))

        assertTrue(observe.await().chargers.isEmpty())
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
    fun `sites a refresh stores show up on the map`() = runBlocking<Unit> {
        val observe = observer(stored = emptyList(), fetched = listOf(site("new", "Ionity", 350.0)))
        observe.await()

        RefreshMapChargers(repository, settings)(RefreshMapChargers.Params(viewport)).getOrThrow()

        assertEquals(listOf("demo:new"), observe.await { it.chargers.isNotEmpty() }.chargers.map { it.site.id })
    }

    @Test
    fun `the stored read gets the padded box and nothing is fetched`() = runBlocking<Unit> {
        val observe = observer(stored = emptyList())
        observe.await()

        assertTrue(fetchedAreas.isEmpty())
        val padded = storedBoxes.first()
        assertTrue(padded.south < viewport.south, "padded box must extend south")
        assertTrue(padded.north > viewport.north, "padded box must extend north")
        assertTrue(padded.west < viewport.west, "padded box must extend west")
        assertTrue(padded.east > viewport.east, "padded box must extend east")
    }

    @Test
    fun `a charger comes with the status stored for it`() = runBlocking<Unit> {
        statusStore.value = mapOf("live-hpc" to listOf(ChargePointStatus(ChargePointState.AVAILABLE)))
        val observe = observer(listOf(site("hpc", "Ionity", 350.0, liveStatusId = "live-hpc"), site("other", "EnBW", 350.0)))

        val chargers = observe.await().chargers.associateBy { it.site.id }

        assertEquals(SiteAvailability.Live(free = 1, total = 1), chargers.getValue("demo:hpc").availability)
        assertNull(chargers.getValue("demo:other").availability)
    }

    @Test
    fun `the availability counts only live points from the minimum power`() = runBlocking<Unit> {
        fun point(state: ChargePointState, kw: Double) = ChargePointStatus(state, kw, listOf(ConnectorType.CCS2))
        statusStore.value = mapOf(
            "live-mixed" to listOf(
                point(ChargePointState.AVAILABLE, 150.0),
                point(ChargePointState.AVAILABLE, 150.0),
                point(ChargePointState.AVAILABLE, 300.0),
                point(ChargePointState.AVAILABLE, 300.0),
            ),
            "live-weak" to listOf(point(ChargePointState.AVAILABLE, 150.0)),
        )
        val observe = observer(
            listOf(
                site("mixed", "Ionity", 300.0, liveStatusId = "live-mixed"),
                site("weak", "EnBW", 300.0, liveStatusId = "live-weak"),
            ),
        ) { setChargeFilters(ChargeFilters(minPowerKw = 300.0)) }

        val chargers = observe.await().chargers.associateBy { it.site.id }

        assertEquals(SiteAvailability.Live(free = 2, total = 2), chargers.getValue("demo:mixed").availability)
        // The site data says 300 kW, the live data doesn't: shown, but without a count.
        assertNull(chargers.getValue("demo:weak").availability)
    }

    @Test
    fun `statuses a refresh stores show up on the map`() = runBlocking<Unit> {
        val observe = observer(listOf(site("hpc", "Ionity", 350.0, liveStatusId = "live-hpc")))
        observe.await()

        statusStore.value = mapOf("live-hpc" to listOf(ChargePointStatus(ChargePointState.OUT_OF_ORDER)))

        val charger = observe.await { result -> result.chargers.any { it.availability != null } }.chargers.single()
        assertEquals(SiteAvailability.OutOfOrder, charger.availability)
    }

    @Test
    fun `the availability refresh asks for the chargers the map shows`() = runBlocking<Unit> {
        observer(
            listOf(
                site("hpc", "Ionity", 350.0, liveStatusId = "live-hpc"),
                site("unknown", "EnBW", 350.0),
                site("wallbox", "Stadtwerke", 22.0, ConnectorType.TYPE2, liveStatusId = "live-wallbox"),
            ),
        )

        RefreshChargerAvailability(repository, statusRepository, settings)(RefreshChargerAvailability.Params(viewport)).getOrThrow()

        assertEquals(listOf(listOf("live-hpc")), refreshedIds.map { it.toList() })
    }
}
