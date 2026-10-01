package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.flowOf

import kotlinx.coroutines.flow.Flow

import org.julakali.chargeahead.shared.domain.MapFilter

import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.ViewportArea
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RefreshMapChargersTest {

    private val viewport = BoundingBox(south = 51.0, west = 6.5, north = 51.4, east = 7.0)

    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val fetchedAreas = mutableListOf<SearchArea>()
    private val fetchedNetworks = mutableListOf<Set<String>>()

    private val refresh = RefreshMapChargersInteractor(
        repository = object : SiteRepository {
            override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = flowOf(emptyList())
            override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = flowOf(emptyList())
            override suspend fun invalidate() {}
            override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> {
                fetchedAreas += area
                fetchedNetworks += networkKeys
                return emptyList()
            }
        },
        preferences = preferences,
    )

    @Test
    fun `fetch uses the strict viewport`() = runBlocking<Unit> {
        refresh(RefreshMapChargersInteractor.Params(viewport)).getOrThrow()

        assertEquals(viewport, (fetchedAreas.single() as ViewportArea).boundingBox)
    }

    @Test
    fun `viewport fetch passes the network selection to the repository`() = runBlocking<Unit> {
        preferences.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))

        refresh(RefreshMapChargersInteractor.Params(viewport)).getOrThrow()

        assertTrue(
            "fastned" in fetchedNetworks.single(),
            "Fastned must appear in the forwarded selection",
        )
    }

    @Test
    fun `slow mode fetches every network`() = runBlocking<Unit> {
        preferences.setChargeFilters(ChargeFilters(slowMode = true))
        preferences.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))

        refresh(RefreshMapChargersInteractor.Params(viewport)).getOrThrow()

        assertEquals(listOf(emptySet()), fetchedNetworks)
    }

    @Test
    fun `a failing fetch comes back as a failure`() = runBlocking<Unit> {
        val failing = RefreshMapChargersInteractor(
            repository = object : SiteRepository {
                override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = flowOf(emptyList())
                override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = flowOf(emptyList())
                override suspend fun invalidate() {}
                override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> =
                    error("offline")
            },
            preferences = preferences,
        )

        assertTrue(failing(RefreshMapChargersInteractor.Params(viewport)).isFailure)
    }
}
