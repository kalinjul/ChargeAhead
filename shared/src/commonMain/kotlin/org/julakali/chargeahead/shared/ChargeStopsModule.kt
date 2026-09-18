package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.core.TripPlanner
import org.julakali.chargeahead.shared.data.BackendChargePointStatusSource
import org.julakali.chargeahead.shared.data.BackendChargeSiteSource
import org.julakali.chargeahead.shared.data.BackendGeocoder
import org.julakali.chargeahead.shared.data.BackendRouteEngine
import org.julakali.chargeahead.shared.data.CombinedSoCSource
import org.julakali.chargeahead.shared.data.DemoSiteSource
import org.julakali.chargeahead.shared.data.ManualSoCSource
import org.julakali.chargeahead.shared.data.MergingSiteRepository
import org.julakali.chargeahead.shared.data.NominatimGeocoder
import org.julakali.chargeahead.shared.data.OpenChargeMapSource
import org.julakali.chargeahead.shared.data.OperatorCatalog
import org.julakali.chargeahead.shared.data.OsrmRouteEngine
import org.julakali.chargeahead.shared.data.SiteFetchActivity
import org.julakali.chargeahead.shared.data.TiledSiteRepository
import org.julakali.chargeahead.shared.data.createHttpClient
import org.julakali.chargeahead.shared.data.pruneCache
import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.ChargeSiteSource
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.ObserveMapChargers
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.TimeProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * Platform-provided configuration: the OpenChargeMap key and the backend.
 *
 * @param openChargeMapKey `null` or empty if none is configured.
 * @param backend if set, replaces the direct provider sources and the key is
 *   not needed.
 */
class ChargeStopsConfig(openChargeMapKey: String?, val backend: BackendConfig?) {
    val openChargeMapKey: String? = openChargeMapKey?.trim()?.takeIf { it.isNotEmpty() }

    /** Neither backend nor key: [DemoSiteSource] stands in. */
    val isDemo: Boolean get() = backend == null && openChargeMapKey == null
}

/**
 * The data graph behind both features, declared once for both platforms.
 *
 * The platform module supplies [LocationSource] (the phone's),
 * [org.julakali.chargeahead.shared.db.DatabaseFactory], [SettingsStore] and
 * [ChargeStopsConfig]. Everything here is one instance per process.
 */
fun chargeStopsModule(): Module = module {
    single<TimeProvider> { TimeProvider { currentTimeMillis() } }

    single<HttpClient> { createHttpClient() } withOptions { onClose { it?.close() } }
    single<ChargeSiteDatabase> { createChargeSiteDatabase(get()) }

    // Shared by every source's store, so the map's spinner sees all of them.
    single { SiteFetchActivity() }

    single<SiteRepository> {
        val config = get<ChargeStopsConfig>()
        val backend = config.backend
        val key = config.openChargeMapKey
        val primary: ChargeSiteSource = when {
            backend != null -> BackendChargeSiteSource(get(), backend.baseUrl, backend.token)
            key != null -> OpenChargeMapSource(get(), key)
            else -> DemoSiteSource()
        }
        MergingSiteRepository(
            listOf(
                TiledSiteRepository(
                    source = primary,
                    database = get(),
                    time = get(),
                    fetchActivity = get(),
                ),
            ),
        )
    }

    single<RouteEngine> {
        when (val backend = get<ChargeStopsConfig>().backend) {
            null -> OsrmRouteEngine(get())
            else -> BackendRouteEngine(get(), backend.baseUrl, backend.token)
        }
    }

    single<Geocoder> {
        when (val backend = get<ChargeStopsConfig>().backend) {
            null -> NominatimGeocoder(get())
            else -> BackendGeocoder(get(), backend.baseUrl, backend.token)
        }
    }

    single { OperatorCatalog(get()) }
    single { TripPlanner(get(), get()) }
    factory { ObserveMapChargers(get(), get()) }
    single {
        val statusSource = get<ChargeStopsConfig>().backend?.let { backend ->
            BackendChargePointStatusSource(get(), backend.baseUrl, backend.token)
        }
        PlanningFeature(
            tripPlanner = get(),
            repository = get(),
            settings = get(),
            statusSource = statusSource,
            time = get(),
        )
    }

    // The phone's feature. Never closed.
    single<ChargeStopsFeature> { getKoin().newChargeStopsFeature(locationSource = get()) }
}

/**
 * A feature on the graph's shared singletons, owned by the caller.
 *
 * @param hardwareSoCSource optional charge level from the vehicle; wins over
 *   the driver's manual entry.
 */
fun Koin.newChargeStopsFeature(
    locationSource: LocationSource,
    hardwareSoCSource: SoCSource? = null,
): ChargeStopsFeature {
    val settingsStore = get<SettingsStore>()
    val time = get<TimeProvider>()
    val database = get<ChargeSiteDatabase>()
    return ChargeStopsFeature(
        locationSource = locationSource,
        repository = get(),
        operatorCatalog = get(),
        settingsStore = settingsStore,
        socSource = CombinedSoCSource(
            manual = ManualSoCSource(settingsStore, time),
            hardware = hardwareSoCSource,
        ),
        routeEngine = get(),
        geocoder = get(),
        isDemo = get<ChargeStopsConfig>().isDemo,
        onStart = {
            runCatching {
                val keys = settingsStore.networks.first().preferredOperators
                pruneCache(database, keys, time.nowMillis(), TiledSiteRepository.DEFAULT_TTL_MILLIS)
            }
        },
    )
}
