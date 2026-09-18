package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.ObserveChargeNow
import org.julakali.chargeahead.shared.domain.ObserveDestinationSearch
import org.julakali.chargeahead.shared.domain.PlanTrip
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.UpdateArrivalSoc
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChargeStopsModuleTest {

    private val fakeLocationSource = object : LocationSource {
        override val updates: Flow<Fix> = emptyFlow()
    }

    private fun <T> withGraph(key: String?, backend: BackendConfig? = null, block: (Koin) -> T): T {
        val app = koinApplication {
            modules(
                module {
                    single<LocationSource> { fakeLocationSource }
                    single<SettingsStore> { PersistentSettingsStore(InMemoryPreferencesDataStore()) }
                    single { DatabaseFactory() }
                    single { ChargeStopsConfig(key, backend) }
                },
                chargeStopsModule(),
            )
        }
        return try {
            block(app.koin)
        } finally {
            app.close()
        }
    }

    private fun isDemo(key: String?, backend: BackendConfig? = null): Boolean =
        withGraph(key, backend) { it.get<ChargeStopsFeature>().currentState.isDemo }

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
    fun withABackend_noKeyIsNeeded() {
        assertFalse(isDemo(null, BackendConfig("https://backend.example", "token")))
    }

    @Test
    fun aTrailingSpaceDoesNotMakeTheKeyUnusable() {
        // A trailing space in local.properties must be trimmed.
        assertFalse(isDemo("00000000-0000-0000-0000-000000000000 "))
    }

    @Test
    fun theUseCasesResolve() {
        withGraph(null) { koin ->
            koin.get<PlanTrip>()
            koin.get<UpdateArrivalSoc>()
            koin.get<ObserveChargeNow>()
            koin.get<ObserveDestinationSearch>()
        }
    }

    @Test
    fun aSessionFeature_isItsOwn_notThePhoneSingleton() {
        withGraph(null) { koin ->
            val phone = koin.get<ChargeStopsFeature>()
            val session = koin.newChargeStopsFeature(fakeLocationSource)
            assertNotSame(phone, session)
            assertSame(phone, koin.get<ChargeStopsFeature>())
            session.close()
        }
    }
}
