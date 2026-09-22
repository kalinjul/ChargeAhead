package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.ViewportArea
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RefreshMapChargersTest {

    private val viewport = BoundingBox(south = 51.0, west = 6.5, north = 51.4, east = 7.0)

    private val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
    private val fetchedAreas = mutableListOf<SearchArea>()
    private val fetchedNetworks = mutableListOf<Set<String>>()

    private val refresh = RefreshMapChargersInteractor(
        repository = object : SiteRepository {
            override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> {
                fetchedAreas += area
                fetchedNetworks += networkKeys
                return emptyList()
            }
        },
        settings = settings,
    )

    @Test
    fun `fetch uses the strict viewport`() = runBlocking<Unit> {
        refresh(RefreshMapChargersInteractor.Params(viewport)).getOrThrow()

        assertEquals(viewport, (fetchedAreas.single() as ViewportArea).boundingBox)
    }

    @Test
    fun `viewport fetch passes the network selection to the repository`() = runBlocking<Unit> {
        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))

        refresh(RefreshMapChargersInteractor.Params(viewport)).getOrThrow()

        assertTrue(
            "fastned" in fetchedNetworks.single(),
            "Fastned must appear in the forwarded selection",
        )
    }

    @Test
    fun `slow mode fetches every network`() = runBlocking<Unit> {
        settings.setChargeFilters(ChargeFilters(slowMode = true))
        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))

        refresh(RefreshMapChargersInteractor.Params(viewport)).getOrThrow()

        assertEquals(listOf(emptySet()), fetchedNetworks)
    }

    @Test
    fun `a failing fetch comes back as a failure`() = runBlocking<Unit> {
        val failing = RefreshMapChargersInteractor(
            repository = object : SiteRepository {
                override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> =
                    error("offline")
            },
            settings = settings,
        )

        assertTrue(failing(RefreshMapChargersInteractor.Params(viewport)).isFailure)
    }
}
