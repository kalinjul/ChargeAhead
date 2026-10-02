package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.onEach

import org.julakali.chargeahead.shared.testDispatchers
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.CHARGE_NOW_RADIUS_KM
import org.julakali.chargeahead.shared.domain.CHARGE_NOW_SLICE
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeNowResult
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import org.julakali.chargeahead.shared.domain.ChargePointStatus
import org.julakali.chargeahead.shared.domain.ChargePointState
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.RelaxedFilter
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@OptIn(kotlin.ExperimentalStdlibApi::class)
class ObserveChargeNowTest {

    private val here = LatLon(48.0, 11.0)
    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val store = MutableStateFlow<List<ChargeSite>>(emptyList())
    private var fetched: List<ChargeSite> = emptyList()
    private val fetches = mutableListOf<Pair<SearchArea, Set<String>>>()
    private var storeCollectedOn: CoroutineDispatcher? = null

    private val repository = object : SiteRepository {
        override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = flowOf(emptyList())
        override suspend fun invalidate() {}
        override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> {
            fetches += area to networkKeys
            store.update { it + fetched }
            return fetched
        }

        override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> =
            store.onEach { storeCollectedOn = coroutineContext[kotlinx.coroutines.CoroutineDispatcher.Key] }
    }


    private val statusStore = MutableStateFlow<Map<String, List<ChargePointStatus>>>(emptyMap())
    private val refreshedIds = mutableListOf<String>()
    private val statusRepository = object : ChargePointStatusRepository {
        override val statuses: Flow<Map<String, List<ChargePointStatus>>> = statusStore
        override suspend fun refresh(ids: Collection<String>) { refreshedIds += ids }
    }

    private val observe = ChargeNowObserver(repository, statusRepository, preferences, testDispatchers)
    private val refresh = RefreshChargeNowInteractor(repository, statusRepository, preferences)

    /** [northKm] north of [here]. */
    private fun site(id: String, powerKw: Double, northKm: Double, liveStatusId: String? = null) = ChargeSite(
        id = id,
        name = id,
        operator = "Ionity",
        position = LatLon(here.lat + northKm / 111.19, here.lon),
        connectors = listOf(Connector(ConnectorType.CCS2, powerKw, 4)),
        liveStatusId = liveStatusId,
    )

    /** A site that is full or broken is no recommendation; one the live source doesn't know stays. */
    @Test
    fun `full and broken sites are never suggested`() = runBlocking {
        store.value = listOf(
            site("full", 300.0, 1.0, liveStatusId = "live-full"),
            site("broken", 300.0, 1.5, liveStatusId = "live-broken"),
            site("unknown", 300.0, 2.0, liveStatusId = "live-unknown"),
            site("free", 300.0, 2.5, liveStatusId = "live-free"),
            site("silent", 300.0, 3.0),
        )
        statusStore.value = mapOf(
            "live-full" to listOf(ChargePointStatus(ChargePointState.OCCUPIED), ChargePointStatus(ChargePointState.RESERVED)),
            "live-broken" to listOf(ChargePointStatus(ChargePointState.OUT_OF_ORDER)),
            "live-unknown" to emptyList(),
            "live-free" to listOf(ChargePointStatus(ChargePointState.OCCUPIED), ChargePointStatus(ChargePointState.AVAILABLE)),
        )

        observe(ChargeNowObserver.Params(here))

        val result = await { it != null }!!
        assertEquals(listOf("unknown", "free", "silent"), (result.candidates + result.more).map { it.site.id })
    }

    @Test
    fun `the refill asks for the live state of the slice`() = runBlocking {
        store.value = listOf(site("a", 300.0, 1.0, liveStatusId = "live-a"), site("b", 300.0, 2.0))

        refresh(RefreshChargeNowInteractor.Params(here)).getOrThrow()

        assertEquals(listOf("live-a"), refreshedIds)
    }

    private suspend fun await(matching: (ChargeNowResult?) -> Boolean): ChargeNowResult? =
        withTimeout(5_000) { observe.flow.first(matching) }

    @Test
    fun `without a position there is no result`() = runBlocking {
        observe(ChargeNowObserver.Params(position = null))

        assertNull(await { true })
    }

    /** Merging the store's rows is the expensive part; the sheet is animating on main meanwhile. */
    @Test
    fun `the store is read and merged on the computation dispatcher`() = runBlocking {
        store.value = listOf(site("near", 300.0, 1.0))

        observe(ChargeNowObserver.Params(here))
        await { it != null }

        assertEquals(testDispatchers.computation, storeCollectedOn)
    }

    @Test
    fun `the stored sites are ranked nearest first`() = runBlocking {
        store.value = listOf(site("far", 300.0, 5.0), site("near", 300.0, 1.0))

        observe(ChargeNowObserver.Params(here))

        assertEquals(listOf("near", "far"), await { it != null }?.candidates?.map { it.site.id })
    }

    /** A sheet shows a handful; the ranking never looks past the nearest slice of the store. */
    @Test
    fun `the ranking takes the nearest slice of the store, not the region`() = runBlocking {
        store.value = (1..CHARGE_NOW_SLICE + 5).map { site("s$it", 300.0, it * 0.1) }

        observe(ChargeNowObserver.Params(here))

        val result = await { it != null }!!
        val all = result.candidates + result.more
        assertEquals(CHARGE_NOW_SLICE, all.size)
        assertEquals((1..CHARGE_NOW_SLICE).map { "s$it" }, all.map { it.site.id })
    }

    @Test
    fun `sites outside the fetch radius are left out`() = runBlocking {
        store.value = listOf(site("near", 300.0, 1.0), site("elsewhere", 300.0, 500.0))

        observe(ChargeNowObserver.Params(here))

        val result = await { it != null }
        assertEquals(listOf("near"), (result!!.candidates + result.more).map { it.site.id })
    }

    /** The sheet follows a filter change while it is open. */
    @Test
    fun `a filter change re-ranks without a refill`() = runBlocking {
        store.value = listOf(site("hpc", 300.0, 1.0), site("mid", 100.0, 2.0), site("mid2", 100.0, 3.0))
        observe(ChargeNowObserver.Params(here))
        // Only one site reaches the default minimum power; the weaker two stay out, the power never gives way.
        assertEquals(listOf("hpc"), await { it != null }?.candidates?.map { it.site.id })

        preferences.setChargeFilters(ChargeFilters(minPowerKw = 50.0))

        assertEquals(3, await { it?.candidates?.size == 3 }?.candidates?.size)
        assertTrue(fetches.isEmpty())
    }

    @Test
    fun `a refill reaches the ranking through the store`() = runBlocking {
        fetched = listOf(site("new", 300.0, 1.0))
        observe(ChargeNowObserver.Params(here))
        assertTrue(await { it != null }!!.isEmpty)

        refresh(RefreshChargeNowInteractor.Params(here)).getOrThrow()

        assertEquals(listOf("new"), await { it?.isEmpty == false }?.candidates?.map { it.site.id })
    }

    @Test
    fun `in ac mode the refill asks for every network`() = runBlocking {
        preferences.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity")))
        preferences.setChargeFilters(ChargeFilters(slowMode = true))

        refresh(RefreshChargeNowInteractor.Params(here)).getOrThrow()

        assertEquals(emptySet(), fetches.single().second)
    }

    @Test
    fun `the refill asks the fixed radius for the selected networks`() = runBlocking {
        preferences.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity")))

        refresh(RefreshChargeNowInteractor.Params(here)).getOrThrow()

        val (area, networks) = fetches.single()
        assertEquals(CHARGE_NOW_RADIUS_KM, area.radiusKm)
        assertEquals(setOf("ionity"), networks)
    }
}
