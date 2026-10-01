package org.julakali.chargeahead.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.TripState
import org.julakali.chargeahead.shared.domain.TripStorage
import org.julakali.chargeahead.shared.domain.usecases.StartAppInteractor
import org.julakali.chargeahead.shared.domain.invoke
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.settingsModule
import org.julakali.chargeahead.shared.ui.HomeViewModel
import org.julakali.chargeahead.shared.ui.PhoneSession
import org.julakali.chargeahead.shared.ui.car.CarChargeNowViewModel
import org.julakali.chargeahead.shared.ui.car.carSession
import org.julakali.chargeahead.shared.ui.car.carUiModule
import org.julakali.chargeahead.shared.ui.phoneSession
import org.julakali.chargeahead.shared.ui.sharedUiModule
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** App start is one use case, and a session (car or iOS caller) gets its own feature through a scope. */
class AppStartAndSessionsTest {

    private val location = object : LocationSource {
        override suspend fun currentFix(): Fix? = null
        override val updates: Flow<Fix> = emptyFlow()
    }

    private class CountingNetworks : NetworkRepository {
        var refreshes = 0
        override val networks: Flow<List<Network>> = MutableStateFlow(emptyList())
        override suspend fun refresh() {
            refreshes++
        }
    }

    private class MemoryTripStorage(var state: TripState?) : TripStorage {
        override suspend fun read(): TripState? = state
        override suspend fun write(state: TripState) {
            this.state = state
        }
    }

    private fun <T> withGraph(networks: NetworkRepository, storage: TripStorage, block: (Koin) -> T): T {
        val app = koinApplication {
            allowOverride(true)
            modules(
                settingsModule { InMemoryPreferencesDataStore() },
                module {
                    single<LocationSource> { location }
                    single { DatabaseFactory() }
                    single<TripStorage> { storage }
                    single { BackendConfig("https://backend.invalid", "token") }
                },
                chargeStopsModule(),
                sharedUiModule(),
                carUiModule(),
                module { single<NetworkRepository> { networks } },
            )
        }
        return try {
            block(app.koin)
        } finally {
            app.close()
        }
    }

    @Test
    fun `starting the app brings the stored trip back and refreshes the networks`() {
        val networks = CountingNetworks()
        val stored = TripState(destination = Destination("München", LatLon(48.137, 11.575)))

        withGraph(networks, MemoryTripStorage(stored)) { koin ->
            runBlocking { koin.get<StartAppInteractor>()().getOrThrow() }

            assertEquals(stored, koin.get<TripRepository>().state.value)
            assertEquals(1, networks.refreshes)
        }
    }

    @Test
    fun `a car session's view models see the session's feature, not the phone's`() {
        withGraph(CountingNetworks(), MemoryTripStorage(null)) { koin ->
            val sessionFeature = koin.get<ChargeStopsFeature>(SessionFeature) { parametersOf(location, null) }
            val session = koin.carSession(sessionFeature)

            assertNotSame(koin.get<ChargeStopsFeature>(), sessionFeature)
            assertSame(sessionFeature, session.get<ChargeStopsFeature>())
            assertSame(sessionFeature, session.get<CarChargeNowViewModel>().feature)
            session.close()
            sessionFeature.close()
        }
    }

    @Test
    fun `an ios caller's phone view models are built from one place too`() {
        withGraph(CountingNetworks(), MemoryTripStorage(null)) { koin ->
            val callerFeature = koin.get<ChargeStopsFeature>(SessionFeature) { parametersOf(location, null) }
            val session = koin.phoneSession(callerFeature)

            assertSame(callerFeature, session.get<HomeViewModel>().feature)
            assertEquals(PhoneSession, session.scopeQualifier)
            session.close()
            callerFeature.close()
        }
    }
}
