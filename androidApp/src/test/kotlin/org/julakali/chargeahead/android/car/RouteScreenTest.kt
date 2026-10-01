package org.julakali.chargeahead.android.car

import android.app.Application
import android.os.Looper
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.testing.TestCarContext
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
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.BackendConfig
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.chargeStopsModule
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripStorage
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.newChargeStopsFeature
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.settingsModule
import org.julakali.chargeahead.shared.ui.car.CarViewModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The route screen on the production graph with the planner, the location
 * and the settings faked. Every dispatcher is unconfined, so a plan is on
 * the screen once the main looper has drained.
 */
@RunWith(RobolectricTestRunner::class)
// The real Application starts the production Koin graph; this test brings its own.
@Config(application = Application::class)
class RouteScreenTest {

    private val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
    private val planner = FakePlanner()
    private val settingsFile = InMemoryPreferencesDataStore()
    private lateinit var feature: ChargeStopsFeature

    @Before
    fun startGraph() {
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            allowOverride(true)
            modules(settingsModule { settingsFile }, chargeStopsModule(), fakes())
        }
        feature = GlobalContext.get().newChargeStopsFeature(locationSource = FakeLocationSource(fix))
        feature.start()
    }

    @After
    fun stopGraph() {
        feature.close()
        stopKoin()
    }

    private fun fakes() = module {
        single { AppCoroutineDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined) }
        single<TripPlanning> { planner }
        single<TripStorage> { TripStorage.None }
        single<NetworkRepository> { NoNetworks }
        single { BackendConfig("http://localhost", "token") }
        single { DatabaseFactory(androidContext()) }
    }

    private fun withVehicle() = runBlocking {
        DataStoreVehicleRepository(settingsFile).setVehicle(
            VehicleProfile("Test-EV", usableBatteryKwh = 75.0, consumptionKwhPer100Km = 18.0, acceptedConnectors = setOf(ConnectorType.CCS2)),
        )
    }

    /** The screen's template once the plan the fake returns has landed. */
    private fun templateFor(result: TripPlanResult): Template {
        planner.result = result
        val screen = RouteScreen(carContext, CarViewModels(GlobalContext.get(), feature), munich)
        shadowOf(Looper.getMainLooper()).idle()
        return screen.onGetTemplate()
    }

    private fun string(id: Int, vararg args: Any) = carContext.getString(id, *args)

    private fun message(template: Template): String =
        (template as MessageTemplate).message.toString()

    @Test
    fun `without a vehicle the screen says so`() {
        assertEquals(string(R.string.car_route_no_vehicle), message(templateFor(TripPlanResult.NoRoute)))
    }

    @Test
    fun `no route is a message`() {
        withVehicle()

        assertEquals(string(R.string.car_route_no_route), message(templateFor(TripPlanResult.NoRoute)))
    }

    @Test
    fun `no charger in reach names the distance`() {
        withVehicle()

        val template = templateFor(TripPlanResult.NoChargerInReach(afterKm = 312.4))

        assertEquals(string(R.string.car_route_no_charger, ChargeStopFormatter.distanceLabel(312.4)), message(template))
    }

    @Test
    fun `a plan without stops is the direct message`() {
        withVehicle()

        assertEquals(string(R.string.car_route_direct), message(templateFor(TripPlanResult.Planned(plan(stops = 0)))))
    }

    @Test
    fun `stops are rows after the send-all row, cut to the host's list limit`() {
        withVehicle()
        val limit = carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val plan = plan(stops = limit + 3)

        val template = templateFor(TripPlanResult.Planned(plan)) as ListTemplate

        val rows = template.singleList!!.items.map { it as Row }
        assertEquals(limit, rows.size)
        assertEquals(string(R.string.car_route_send_all), rows.first().title.toString())
        assertEquals(
            plan.stops.take(limit - 1).mapIndexed { index, stop -> ChargeStopFormatter.plannedStopTitle(index + 1, stop) },
            rows.drop(1).map { it.title.toString() },
        )
        assertTrue(template.isLoading.not())
    }

    @Test
    fun `while planning the list is loading`() {
        withVehicle()
        planner.result = TripPlanResult.NoRoute
        planner.gate = Mutex(locked = true)
        val screen = RouteScreen(carContext, CarViewModels(GlobalContext.get(), feature), munich)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue((screen.onGetTemplate() as ListTemplate).isLoading)

        planner.gate!!.unlock()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(string(R.string.car_route_no_route), message(screen.onGetTemplate()))
    }

    private class FakePlanner : TripPlanning {
        var result: TripPlanResult = TripPlanResult.NoRoute

        /** Locked: the plan waits until unlocked. */
        var gate: Mutex? = null

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
            return result
        }
    }

    private class FakeLocationSource(private val fix: Fix) : LocationSource {
        override val updates: Flow<Fix> = flow {
            emit(fix)
            awaitCancellation()
        }

        override suspend fun currentFix(): Fix = fix
    }

    private object NoNetworks : NetworkRepository {
        override val networks: Flow<List<Network>> = flowOf(emptyList())
        override suspend fun refresh() = Unit
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(mutablePreferencesOf().toPreferences())
        private val mutex = Mutex()

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    private companion object {
        val hamburg = LatLon(53.55, 9.99)
        val munich = Destination("München", LatLon(48.137, 11.575))
        val fix = Fix(hamburg, bearingDeg = null, speedMps = null, timestampMillis = 0L)

        fun site(index: Int) = ChargeSite(
            id = "test:$index",
            name = "Lader $index",
            operator = "Operator $index",
            position = LatLon(hamburg.lat - index * 0.5, hamburg.lon + index * 0.1),
            connectors = listOf(Connector(ConnectorType.CCS2, 300.0, 4)),
        )

        fun plan(stops: Int) = TripPlan(
            route = Route(listOf(hamburg, munich.position), distanceKm = 776.0, durationMinutes = 470.0),
            destination = munich,
            stops = (1..stops).map { index ->
                PlannedStop(
                    site = site(index),
                    kmFromStart = index * 100.0,
                    arrivalSocPercent = 15.0,
                    departureSocPercent = 80.0,
                    chargeKwh = 48.0,
                    chargeMinutes = 24.0,
                    etaMinutesFromStart = index * 70.0,
                    maxPowerKw = 300.0,
                )
            },
            driveMinutes = 470.0,
            chargeMinutes = stops * 24.0,
            arrivalSocPercent = 22.0,
        )
    }
}
