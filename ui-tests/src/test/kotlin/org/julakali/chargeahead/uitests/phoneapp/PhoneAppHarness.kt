package org.julakali.chargeahead.uitests.phoneapp

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.dynamic.IObjectWrapper
import com.google.android.gms.dynamic.ObjectWrapper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.internal.ICameraUpdateFactoryDelegate
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.BackendConfig
import org.julakali.chargeahead.shared.chargeStopsModule
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargePointStatus
import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.DataSource
import org.julakali.chargeahead.shared.domain.DataSourceDirectory
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.distanceKmTo
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import org.julakali.chargeahead.shared.ui.sharedUiModule
import org.julakali.chargeahead.uitests.Fixtures
import org.julakali.chargeahead.uitests.InMemoryPreferencesDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Shadows
import java.lang.reflect.Proxy

/**
 * The production Koin graph with every world-facing port faked: the phone sits
 * in Hamburg, the geocoder knows München, roads are straight lines and three
 * chargers wait along the way. Call [start] before composing, [stop] in @After.
 */
class PhoneAppHarness {
    val hamburg = LatLon(53.55, 9.99)
    val muenchen = LatLon(48.137, 11.575)
    val fix = Fix(hamburg, null, null, 0L)

    /** On the straight line: the planner's corridor is only 3 km wide. */
    val sites: List<ChargeSite> = listOf(
        Fixtures.site("s1", "IONITY GmbH", along(0.25), "Hannover"),
        Fixtures.site("s2", "EnBW", along(0.5), "Kassel"),
        Fixtures.site("s3", "Aral pulse", along(0.75), "Nürnberg"),
    )

    lateinit var settings: SettingsStore
        private set

    fun start(withVehicle: Boolean = true) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        stubCameraUpdates()
        settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        if (withVehicle) runBlocking {
            settings.setVehicle(
                VehicleProfile(
                    displayName = "Test-EV",
                    usableBatteryKwh = 75.0,
                    consumptionKwhPer100Km = 18.0,
                    acceptedConnectors = setOf(ConnectorType.CCS2),
                ),
            )
            settings.setManualSocPercent(80.0)
        }
        startKoin {
            androidContext(app)
            allowOverride(true)
            modules(chargeStopsModule(), sharedUiModule(), fakes())
        }
    }

    fun stop() = stopKoin()

    private fun fakes() = module {
        single<SettingsStore> { settings }
        single<LocationSource> { FakeLocationSource(fix) }
        single<Geocoder> { FakeGeocoder(Place("München", "München, Bayern", muenchen)) }
        single<RouteEngine> { StraightLineRouteEngine() }
        single<SiteRepository> { FakeSiteRepository(sites) }
        single<ChargePointStatusRepository> { NoStatuses }
        single<DataSourceDirectory> { NoDataSources }
        single<NetworkRepository> { NoNetworks }
        single { BackendConfig("http://localhost", "token") }
        single { DatabaseFactory(androidContext()) }
    }

    /**
     * The map never renders under Robolectric, so the SDK never installs the
     * factory behind [CameraUpdateFactory]; the camera code would NPE on the
     * first fix. Opaque tokens keep it happy — nothing ever unwraps them.
     */
    private fun stubCameraUpdates() {
        val delegate = Proxy.newProxyInstance(
            ICameraUpdateFactoryDelegate::class.java.classLoader,
            arrayOf(ICameraUpdateFactoryDelegate::class.java),
        ) { _, method, _ -> if (method.returnType == IObjectWrapper::class.java) ObjectWrapper.wrap(Any()) else null }
        CameraUpdateFactory.zza(delegate as ICameraUpdateFactoryDelegate)
    }

    private fun along(fraction: Double) = LatLon(
        hamburg.lat + (muenchen.lat - hamburg.lat) * fraction,
        hamburg.lon + (muenchen.lon - hamburg.lon) * fraction,
    )
}

private class FakeLocationSource(private val fix: Fix) : LocationSource {
    // One fix, then the stream stays open: an ended stream reads as "location failed".
    override val updates: Flow<Fix> = flow {
        emit(fix)
        awaitCancellation()
    }

    override suspend fun currentFix(): Fix = fix
}

private class FakeGeocoder(private val hit: Place) : Geocoder {
    override suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> =
        if (query.contains("münch", ignoreCase = true)) listOf(hit) else emptyList()
}

private class StraightLineRouteEngine : RouteEngine {
    override suspend fun route(from: LatLon, to: LatLon): Route {
        val distanceKm = from.distanceKmTo(to)
        return Route(points = listOf(from, to), distanceKm = distanceKm, durationMinutes = distanceKm / 100 * 60)
    }
}

private class FakeSiteRepository(private val sites: List<ChargeSite>) : SiteRepository {
    override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> = sites
    override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = flowOf(sites)
    override fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>> = flowOf(sites)
}

private object NoStatuses : ChargePointStatusRepository {
    override val statuses: Flow<Map<String, List<ChargePointStatus>>> = flowOf(emptyMap())
    override suspend fun refresh(ids: Collection<String>) = Unit
}

private object NoDataSources : DataSourceDirectory {
    override suspend fun dataSources(): List<DataSource> = emptyList()
}

private object NoNetworks : NetworkRepository {
    override val networks: Flow<List<Network>> = flowOf(emptyList())
    override suspend fun refresh() = Unit
}
