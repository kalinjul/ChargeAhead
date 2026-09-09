package de.autoapp.shared

import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LocationSource
import kotlinx.coroutines.flow.Flow
import de.autoapp.shared.db.DatabaseFactory
import de.autoapp.shared.settings.InMemoryKeyValueStorage
import de.autoapp.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChargeStopsFeatureFactoryTest {

    private val fakeLocationSource = object : LocationSource {
        override val updates: Flow<Fix> = emptyFlow()
    }

    private fun isDemo(key: String?): Boolean =
        ChargeStopsFeatureFactory.create(
            locationSource = fakeLocationSource,
            openChargeMapKey = key,
            settingsStore = PersistentSettingsStore(InMemoryKeyValueStorage()),
            databaseFactory = DatabaseFactory(),
        ).currentState.isDemo

    @Test
    fun withoutAKey_theDemoSourceIsUsed() {
        assertTrue(isDemo(null))
    }

    @Test
    fun anEmptyKey_countsAsNone() {
        assertTrue(isDemo(""))
    }

    @Test
    fun aKeyOfOnlySpaces_countsAsNone() {
        assertTrue(isDemo("   "))
    }

    @Test
    fun withAKey_theRealSourceIsUsed() {
        assertFalse(isDemo("00000000-0000-0000-0000-000000000000"))
    }

    @Test
    fun aTrailingSpaceDoesNotMakeTheKeyUnusable() {
        // The classic mistake in local.properties and .xcconfig. Left untrimmed,
        // the space gets URL-encoded into the request and OCM responds with
        // "Invalid API key" without saying why.
        assertFalse(isDemo("00000000-0000-0000-0000-000000000000 "))
    }
}
