package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.CHARGE_NOW_RELAX_FETCH_FACTOR
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeNowResult
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.RelaxedFilter
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
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

class ObserveChargeNowTest {

    private val here = LatLon(48.0, 11.0)
    private val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
    private val store = MutableStateFlow<List<ChargeSite>>(emptyList())
    private var fetched: List<ChargeSite> = emptyList()
    private val fetches = mutableListOf<Pair<SearchArea, Set<String>>>()

    private val repository = object : SiteRepository {
        override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> {
            fetches += area to networkKeys
            store.update { it + fetched }
            return fetched
        }

        override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = store
    }

    private val observe = ChargeNowObserver(repository, settings)
    private val refresh = RefreshChargeNowInteractor(repository, settings)

    /** [northKm] north of [here]. */
    private fun site(id: String, powerKw: Double, northKm: Double) = ChargeSite(
        id = id,
        name = id,
        operator = "Ionity",
        position = LatLon(here.lat + northKm / 111.19, here.lon),
        connectors = listOf(Connector(ConnectorType.CCS2, powerKw, 4)),
    )

    private suspend fun await(matching: (ChargeNowResult?) -> Boolean): ChargeNowResult? =
        withTimeout(5_000) { observe.flow.first(matching) }

    @Test
    fun `without a position there is no result`() = runBlocking {
        observe(ChargeNowObserver.Params(position = null))

        assertNull(await { true })
    }

    @Test
    fun `the stored sites are ranked nearest first`() = runBlocking {
        store.value = listOf(site("far", 300.0, 5.0), site("near", 300.0, 1.0))

        observe(ChargeNowObserver.Params(here))

        assertEquals(listOf("near", "far"), await { it != null }?.candidates?.map { it.site.id })
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
        // Only one site reaches the default minimum power, so the ranking relaxes it.
        assertEquals(listOf(RelaxedFilter.MIN_POWER), await { it != null }?.relaxed)

        settings.setChargeFilters(ChargeFilters(minPowerKw = 50.0))

        assertEquals(3, await { it?.relaxed?.isEmpty() == true }?.candidates?.size)
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
    fun `the refill asks wider than the distance filter for the selected networks`() = runBlocking {
        settings.setChargeFilters(ChargeFilters(maxDistanceKm = 10.0))
        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity")))

        refresh(RefreshChargeNowInteractor.Params(here)).getOrThrow()

        val (area, networks) = fetches.single()
        assertEquals(10.0 * CHARGE_NOW_RELAX_FETCH_FACTOR, area.radiusKm)
        assertEquals(setOf("ionity"), networks)
    }
}
