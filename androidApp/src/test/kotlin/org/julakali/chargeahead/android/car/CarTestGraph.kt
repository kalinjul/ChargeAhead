package org.julakali.chargeahead.android.car

import android.os.Looper
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.Action
import androidx.car.app.model.CarText
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.GridItem
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestScreenManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.julakali.chargeahead.shared.BackendConfig
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.SessionFeature
import org.julakali.chargeahead.shared.chargeStopsModule
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.VehicleCatalogRepository
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.TripStorage
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.usecases.CommitTripInteractor
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.settingsModule
import org.julakali.chargeahead.shared.ui.car.carSession
import org.julakali.chargeahead.shared.ui.car.carUiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf

/**
 * The production graph for a car screen test: planner, location, charge
 * state and stored sites faked, settings in memory, every dispatcher
 * unconfined. Build it in `@Before`, close it in `@After`.
 */
class CarTestGraph(
    fix: Fix? = hamburgFix,
    /** `null`: no charge reading at all; with a kind, the feature reports that level from that source. */
    energy: EnergyState? = null,
) {
    val carContext: TestCarContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
    val planner = FakePlanner()
    val sites = FakeSiteRepository()
    val settingsFile = InMemoryPreferencesDataStore()
    val feature: ChargeStopsFeature
    val session: Scope
    val permissions = CarPermissions(carContext)

    init {
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            allowOverride(true)
            modules(settingsModule { settingsFile }, chargeStopsModule(), carUiModule(), fakes())
        }
        val socSource = energy?.let { state ->
            object : SoCSource {
                override val kind: SoCSourceKind = state.source
                override val energy: Flow<EnergyState?> = flowOf(state)
            }
        }
        feature = GlobalContext.get().get(SessionFeature) { parametersOf(FakeLocationSource(fix), socSource) }
        feature.start()
        session = GlobalContext.get().carSession(feature)
        shadowOf(Looper.getMainLooper()).idle()
    }

    fun close() {
        feature.close()
        stopKoin()
    }

    private fun fakes() = module {
        single { AppCoroutineDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined) }
        single<TripPlanning> { planner }
        single<TripStorage> { TripStorage.None }
        single<SiteRepository> { sites }
        single<NetworkRepository> { NoNetworks }
        single<VehicleCatalogRepository> { NoCatalog }
        single { BackendConfig("http://localhost", "token") }
        single { DatabaseFactory(androidContext()) }
    }

    fun withVehicle() = runBlocking {
        DataStoreVehicleRepository(settingsFile).setVehicle(
            VehicleProfile("Test-EV", usableBatteryKwh = 75.0, consumptionKwhPer100Km = 18.0, acceptedConnectors = setOf(ConnectorType.CCS2)),
        )
    }

    /** Commits [plan] the way the phone does, so the car sees an active route. */
    fun withCommittedTrip(plan: TripPlan) = runBlocking {
        GlobalContext.get().get<CommitTripInteractor>()(CommitTripInteractor.Params(plan, startSocPercent = 60.0)).getOrThrow()
        shadowOf(Looper.getMainLooper()).idle()
    }

    val screens: TestScreenManager get() = carContext.getCarService(TestScreenManager::class.java)

    val trips: TripRepository get() = GlobalContext.get().get()

    class FakePlanner : TripPlanning {
        var result: TripPlanResult = TripPlanResult.NoRoute

        /** Locked: the plan waits until unlocked. */
        var gate: Mutex? = null

        /** Every start level the planner was asked with. */
        val startLevels = mutableListOf<Double>()

        override suspend fun plan(
            from: LatLon,
            destination: Destination,
            vehicle: VehicleProfile,
            startSocPercent: Double,
            arrivalSocPercent: Double,
            filters: ChargeFilters,
            networks: NetworkPreferences,
        ): TripPlanResult {
            gate?.withLock { }
            startLevels += startSocPercent
            return result
        }
    }

    /** Stored sites are whatever [stored] holds; [load] returns them too and counts. */
    class FakeSiteRepository : SiteRepository {
        val stored = MutableStateFlow<List<ChargeSite>>(emptyList())
        var loads = 0

        override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> {
            loads += 1
            return stored.value
        }

        override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = stored

        override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = stored

        override suspend fun invalidate() = Unit
    }

    private class FakeLocationSource(private val fix: Fix?) : LocationSource {
        override val updates: Flow<Fix> = flow {
            fix?.let { emit(it) }
            awaitCancellation()
        }

        override suspend fun currentFix(): Fix = fix ?: awaitCancellation()
    }

    private object NoNetworks : NetworkRepository {
        override val networks: Flow<List<Network>> = flowOf(emptyList())
        override suspend fun refresh() = Unit
    }

    private object NoCatalog : VehicleCatalogRepository {
        override val presets: Flow<List<VehiclePreset>> = flowOf(emptyList())
        override suspend fun refresh() = Unit
    }

    class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(mutablePreferencesOf().toPreferences())
        private val mutex = Mutex()

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    companion object {
        val hamburg = LatLon(53.55, 9.99)
        val munich = Destination("München", LatLon(48.137, 11.575))
        val hamburgFix = Fix(hamburg, bearingDeg = null, speedMps = null, timestampMillis = 0L)

        fun site(index: Int, liveStatusId: String? = null) = ChargeSite(
            id = "test:$index",
            name = "Lader $index",
            operator = "Operator $index",
            position = LatLon(hamburg.lat - index * 0.5, hamburg.lon + index * 0.1),
            connectors = listOf(Connector(ConnectorType.CCS2, 300.0, 4)),
            liveStatusId = liveStatusId,
        )

        fun plan(stops: Int, arrivalSocPercent: Double = 15.0) = TripPlan(
            route = Route(listOf(hamburg, munich.position), distanceKm = 776.0, durationMinutes = 470.0),
            destination = munich,
            stops = (1..stops).map { index ->
                PlannedStop(
                    site = site(index),
                    kmFromStart = index * 100.0,
                    arrivalSocPercent = arrivalSocPercent,
                    departureSocPercent = 80.0,
                    chargeMinutes = 24.0,
                    chargeKwh = 48.0,
                    etaMinutesFromStart = index * 70.0,
                    maxPowerKw = 300.0,
                )
            },
            driveMinutes = 470.0,
            chargeMinutes = stops * 24.0,
            arrivalSocPercent = 22.0,
        )

        /** Lets the main looper deliver what the flows emitted. */
        fun settle() = shadowOf(Looper.getMainLooper()).idle()

        /** Taps the row or action the way the host does. */
        fun click(row: Row) = row.onClickDelegate!!.sendClick(NoCallback).also { settle() }

        fun click(action: Action) = action.onClickDelegate!!.sendClick(NoCallback).also { settle() }

        fun click(tile: GridItem) = tile.onClickDelegate!!.sendClick(NoCallback).also { settle() }

        /** The distance the host would format, in the unit the span carries; `null` without a span. */
        fun distanceOf(text: CarText?): Double? =
            text?.spans?.map { it.carSpan }?.filterIsInstance<DistanceSpan>()?.firstOrNull()?.distance?.displayDistance

        private object NoCallback : OnDoneCallback
    }
}
