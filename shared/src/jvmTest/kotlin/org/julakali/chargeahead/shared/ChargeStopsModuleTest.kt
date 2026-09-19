package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.ObserveChargeNow
import org.julakali.chargeahead.shared.domain.ObserveChargeStops
import org.julakali.chargeahead.shared.domain.ObserveDestinationSearch
import org.julakali.chargeahead.shared.domain.PlanTrip
import org.julakali.chargeahead.shared.domain.RefreshNetworks
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
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ChargeStopsModuleTest {

    private val fakeLocationSource = object : LocationSource {
        override val updates: Flow<Fix> = emptyFlow()
    }

    private fun <T> withGraph(block: (Koin) -> T): T {
        val app = koinApplication {
            modules(
                module {
                    single<LocationSource> { fakeLocationSource }
                    single<SettingsStore> { PersistentSettingsStore(InMemoryPreferencesDataStore()) }
                    single { DatabaseFactory() }
                    single { BackendConfig("https://backend.invalid", "token") }
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

    @Test
    fun theUseCasesResolve() {
        withGraph { koin ->
            koin.get<PlanTrip>()
            koin.get<UpdateArrivalSoc>()
            koin.get<ObserveChargeNow>()
            koin.get<ObserveChargeStops>()
            koin.get<ObserveDestinationSearch>()
            koin.get<RefreshNetworks>()
            koin.get<NetworkRepository>()
        }
    }

    @Test
    fun aSessionFeature_isItsOwn_notThePhoneSingleton() {
        withGraph { koin ->
            val phone = koin.get<ChargeStopsFeature>()
            val session = koin.newChargeStopsFeature(fakeLocationSource)
            assertNotSame(phone, session)
            assertSame(phone, koin.get<ChargeStopsFeature>())
            session.close()
        }
    }
}
