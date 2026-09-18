package org.julakali.chargeahead.shared.domain

import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RefreshMapChargersTest {

    private val viewport = BoundingBox(south = 51.0, west = 6.5, north = 51.4, east = 7.0)

    private val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
    private val fetchedAreas = mutableListOf<SearchArea>()
    private val fetchedNetworks = mutableListOf<List<Network>>()

    private val refresh = RefreshMapChargers(
        repository = object : SiteRepository {
            override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> {
                fetchedAreas += area
                fetchedNetworks += networks
                return emptyList()
            }
        },
        settings = settings,
    )

    @Test
    fun `fetch uses the strict viewport`() = runBlocking<Unit> {
        refresh(RefreshMapChargers.Params(viewport)).getOrThrow()

        assertEquals(viewport, (fetchedAreas.single() as ViewportArea).boundingBox)
    }

    @Test
    fun `viewport fetch passes the network selection to the repository`() = runBlocking<Unit> {
        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))

        refresh(RefreshMapChargers.Params(viewport)).getOrThrow()

        assertTrue(
            fetchedNetworks.single().any { it.key == "fastned" },
            "Fastned must appear in the forwarded selection",
        )
    }

    @Test
    fun `slow mode fetches every network`() = runBlocking<Unit> {
        settings.setChargeFilters(ChargeFilters(slowMode = true))
        settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned")))

        refresh(RefreshMapChargers.Params(viewport)).getOrThrow()

        assertEquals(listOf(emptyList()), fetchedNetworks)
    }

    @Test
    fun `a failing fetch comes back as a failure`() = runBlocking<Unit> {
        val failing = RefreshMapChargers(
            repository = object : SiteRepository {
                override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> =
                    error("offline")
            },
            settings = settings,
        )

        assertTrue(failing(RefreshMapChargers.Params(viewport)).isFailure)
    }
}
