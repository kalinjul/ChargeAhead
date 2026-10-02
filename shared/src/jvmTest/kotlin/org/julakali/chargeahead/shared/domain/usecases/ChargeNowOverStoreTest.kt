package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.data.MergingSiteRepository
import org.julakali.chargeahead.shared.data.TiledSiteRepository
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeNowResult
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeSiteSource
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.destination
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testAppScope
import org.julakali.chargeahead.shared.testDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The ranking over the real store: SQL slice, merge, filters, in one piece. */
class ChargeNowOverStoreTest {

    private val here = LatLon(48.137, 11.575)

    private fun site(id: String, operator: String, powerKw: Double, bearingDeg: Double, distanceKm: Double, type: ConnectorType = ConnectorType.CCS2) = ChargeSite(
        id = id,
        name = id,
        operator = operator,
        position = here.destination(bearingDeg, distanceKm),
        connectors = listOf(Connector(type, powerKw, 4)),
        networkKey = operator.lowercase(),
    )

    private val stored = buildList {
        // A city block of AC posts, nearer than anything strong.
        repeat(35) { add(site("weak$it", "Stadtwerke", 22.0, it * 10.0, 0.5 + it * 0.02, ConnectorType.TYPE2)) }
        add(site("ionity-a", "Ionity", 350.0, 10.0, 3.0))
        add(site("ionity-b", "Ionity", 350.0, 200.0, 4.0))
        add(site("far", "Ionity", 350.0, 90.0, 130.0))
    }

    private val source = object : ChargeSiteSource {
        override val id = "test"
        override suspend fun query(area: SearchArea, networkKeys: Set<String>) = stored
    }

    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())

    private suspend fun rankOverStore(): ChargeNowResult {
        val database = createChargeSiteDatabase(DatabaseFactory(), Dispatchers.IO)
        val tiled = TiledSiteRepository(source = source, database = database, time = TimeProvider { 1_000L }, scope = testAppScope)
        val repository = MergingSiteRepository(listOf(tiled), computation = Dispatchers.Default)
        repository.load(SectorArea.circle(here, 200.0))
        val observe = ChargeNowObserver(repository, preferences, testDispatchers)
        observe(ChargeNowObserver.Params(here))
        return withTimeout(5_000) { observe.flow.first { it != null } }!!
    }

    @Test
    fun `ac mode lists the slow posts nearest first, strong ones stay out`() = runBlocking {
        preferences.setChargeFilters(ChargeFilters(minPowerKw = 150.0, slowMode = true))
        preferences.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity")))

        val result = rankOverStore()

        assertEquals(emptyList(), result.relaxed)
        assertEquals(listOf("weak0", "weak1", "weak2"), result.candidates.map { it.site.id })
        assertTrue((result.candidates + result.more).none { it.site.operator == "Ionity" })
    }

    @Test
    fun `nearby weak posts do not push the ranking into relaxing, and 130 km stays out`() = runBlocking {
        preferences.setChargeFilters(ChargeFilters(minPowerKw = 150.0))
        preferences.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity")))

        val result = rankOverStore()

        assertEquals(emptyList(), result.relaxed)
        assertEquals(listOf("ionity-a", "ionity-b"), result.candidates.map { it.site.id })
        assertTrue((result.candidates + result.more).none { it.site.id == "far" })
    }
}
