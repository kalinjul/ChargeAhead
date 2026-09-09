package de.autoapp.shared

import de.autoapp.shared.core.TripPlanner
import de.autoapp.shared.data.BnetzaSource
import de.autoapp.shared.data.CombinedSoCSource
import de.autoapp.shared.data.MergingSiteRepository
import de.autoapp.shared.data.DemoSiteSource
import de.autoapp.shared.data.OperatorCatalog
import de.autoapp.shared.data.DemoTariffSource
import de.autoapp.shared.data.ManualSoCSource
import de.autoapp.shared.data.NominatimGeocoder
import de.autoapp.shared.data.OpenChargeMapSource
import de.autoapp.shared.data.OsrmRouteEngine
import de.autoapp.shared.data.TiledSiteRepository
import de.autoapp.shared.data.createHttpClient
import de.autoapp.shared.data.pruneCache
import de.autoapp.shared.db.DatabaseFactory
import de.autoapp.shared.db.createChargeSiteDatabase
import de.autoapp.shared.domain.ChargeSiteSource
import de.autoapp.shared.domain.LocationSource
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.SoCSource
import de.autoapp.shared.domain.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Assembles the feature from its parts.
 *
 * This assembly belongs in the shared module so Android and iOS don't decide
 * separately what happens without an API key — otherwise one platform would
 * show demo data and the other an empty list. The platforms only supply what
 * they must: location and the key.
 */
object ChargeStopsFeatureFactory {

    /**
     * @param openChargeMapKey `null` or empty if none is configured — then
     *   [DemoSiteSource] takes the place of the real source, and
     *   `ChargeStopsState.isDemo` tells the UI it must indicate that.
     */
    /**
     * @param hardwareSoCSource optional upgrade from the vehicle. If it
     *   supplies a value, it wins over the driver's manual entry; on iOS there
     *   is none, and in Android Auto projection only rarely.
     */
    fun create(
        locationSource: LocationSource,
        openChargeMapKey: String?,
        settingsStore: SettingsStore,
        databaseFactory: DatabaseFactory,
        hardwareSoCSource: SoCSource? = null,
        timeProvider: TimeProvider = TimeProvider { currentTimeMillis() },
    ): ChargeStopsFeature {
        // Trimmed, not just checked for emptiness: a trailing space from
        // local.properties or an .xcconfig would otherwise travel URL-encoded
        // into the request, and OCM would respond with "Invalid API key".
        val key = openChargeMapKey?.trim()?.takeIf { it.isNotEmpty() }

        // One for everything: charge sites, routes, and geocoding share the
        // connection pool. It is created even without an OCM key, because
        // routing and geocoding don't need one.
        val httpClient = createHttpClient()
        val database = createChargeSiteDatabase(databaseFactory)

        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                val keys = settingsStore.networks.first().preferredOperators
                pruneCache(database, keys, timeProvider.nowMillis(), TiledSiteRepository.DEFAULT_TTL_MILLIS)
            }
        }

        // Each source gets its own store and thus its own tile coverage: the
        // official register covers only Germany, OpenChargeMap the whole
        // world. A shared coverage record would falsely claim that a tile
        // beyond the border had been checked.
        val primary: ChargeSiteSource = if (key != null) {
            OpenChargeMapSource(httpClient, key)
        } else {
            DemoSiteSource()
        }

        val sources = buildList {
            add(primary)
            // Without an OCM key, the app runs on demo data; adding the
            // official register alongside it would undermine that labeling.
            if (key != null) add(BnetzaSource(httpClient))
        }

        val repository: SiteRepository = MergingSiteRepository(
            sources.map { source ->
                TiledSiteRepository(source = source, database = database, time = timeProvider)
            },
        )

        val routeEngine = OsrmRouteEngine(httpClient)
        // Prices are always the demo table until a real price API is chosen
        // (ROADMAP) — unlike charge sites, there is no keyed source to prefer.
        val tariffSource = DemoTariffSource()

        return ChargeStopsFeature(
            locationSource = locationSource,
            repository = repository,
            operatorCatalog = OperatorCatalog(database),
            settingsStore = settingsStore,
            socSource = CombinedSoCSource(
                manual = ManualSoCSource(settingsStore, timeProvider),
                hardware = hardwareSoCSource,
            ),
            routeEngine = routeEngine,
            geocoder = NominatimGeocoder(httpClient),
            isDemo = key == null,
            onClose = { httpClient.close() },
            planning = PlanningFeature(
                tripPlanner = TripPlanner(routeEngine, repository, tariffSource),
                repository = repository,
                tariffs = tariffSource,
                settings = settingsStore,
            ),
        )
    }
}
