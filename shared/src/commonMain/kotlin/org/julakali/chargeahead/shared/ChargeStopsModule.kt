package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.core.TripPlanner
import org.julakali.chargeahead.shared.data.BackendChargeSiteSource
import org.julakali.chargeahead.shared.data.BackendGeocoder
import org.julakali.chargeahead.shared.data.BackendRouteEngine
import org.julakali.chargeahead.shared.data.BnetzaSource
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
 * What the platform knows and the graph doesn't: the OpenChargeMap key and
 * the backend.
 *
 * @param openChargeMapKey `null` or empty if none is configured. Trimmed, not
 *   just checked for emptiness: a trailing space from local.properties or an
 *   .xcconfig would otherwise travel URL-encoded into the request, and OCM
 *   would respond with "Invalid API key".
 * @param backend set, it replaces the direct provider sources and the key is
 *   not needed; `null` keeps the app talking to the providers itself.
 */
class ChargeStopsConfig(openChargeMapKey: String?, val backend: BackendConfig?) {
    val openChargeMapKey: String? = openChargeMapKey?.trim()?.takeIf { it.isNotEmpty() }

    /** Neither backend nor key: [DemoSiteSource] stands in, and the UI must say so. */
    val isDemo: Boolean get() = backend == null && openChargeMapKey == null
}

/**
 * The data graph behind both features, declared once for both platforms so
 * Android and iOS don't decide separately what happens without an API key —
 * otherwise one would show demo data and the other an empty list.
 *
 * The platform module supplies only what it must: [LocationSource] (the
 * phone's), [org.julakali.chargeahead.shared.db.DatabaseFactory], [SettingsStore] and
 * [ChargeStopsConfig]. Everything here is one instance per process, so the
 * phone and a car session share one connection pool, one database and one
 * repository — two SQLite connections on the same file would race.
 */
fun chargeStopsModule(): Module = module {
    single<TimeProvider> { TimeProvider { currentTimeMillis() } }

    // One for everything: charge sites, routes, and geocoding share the
    // connection pool. Created even without an OCM key, because routing and
    // geocoding don't need one.
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
        val sources = buildList {
            add(primary)
            // On demo data, adding the official register alongside it would
            // undermine that labeling.
            if (!config.isDemo) add(BnetzaSource(get()))
        }
        // Each source gets its own store and thus its own tile coverage: the
        // official register covers only Germany, OpenChargeMap the whole
        // world. A shared coverage record would falsely claim that a tile
        // beyond the border had been checked.
        MergingSiteRepository(
            sources.map { source ->
                TiledSiteRepository(
                    source = source,
                    database = get(),
                    time = get(),
                    fetchActivity = get(),
                )
            },
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
    single { PlanningFeature(tripPlanner = get(), repository = get(), settings = get()) }

    // The phone's feature: no vehicle access, just location and manual input.
    // Never closed — its lifetime is the process.
    single<ChargeStopsFeature> { getKoin().newChargeStopsFeature(locationSource = get()) }
}

/**
 * A feature on the graph's shared singletons, owned by the caller: the phone's
 * is the [chargeStopsModule] single, a car session builds its own with the
 * car's location and battery and closes it with the session.
 *
 * @param hardwareSoCSource optional upgrade from the vehicle. If it supplies a
 *   value, it wins over the driver's manual entry; on iOS there is none, and
 *   in Android Auto projection only rarely.
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
        // Prune stale cache on the feature's own scope, not a detached one.
        onStart = {
            runCatching {
                val keys = settingsStore.networks.first().preferredOperators
                pruneCache(database, keys, time.nowMillis(), TiledSiteRepository.DEFAULT_TTL_MILLIS)
            }
        },
    )
}
