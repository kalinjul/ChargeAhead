package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.core.CorridorPlanner
import org.julakali.chargeahead.shared.core.TripPlanner
import org.julakali.chargeahead.shared.data.BackendChargePointStatusSource
import org.julakali.chargeahead.shared.data.CachingChargePointStatusRepository
import org.julakali.chargeahead.shared.data.BackendChargeSiteSource
import org.julakali.chargeahead.shared.data.BackendDataSourceDirectory
import org.julakali.chargeahead.shared.data.BackendGeocoder
import org.julakali.chargeahead.shared.data.BackendNetworkListSource
import org.julakali.chargeahead.shared.data.BackendRouteEngine
import org.julakali.chargeahead.shared.data.CombinedSoCSource
import org.julakali.chargeahead.shared.data.ManualSoCSource
import org.julakali.chargeahead.shared.data.MergingSiteRepository
import org.julakali.chargeahead.shared.data.RoomTripStorage
import org.julakali.chargeahead.shared.data.RoomNetworkRepository
import org.julakali.chargeahead.shared.data.TiledSiteRepository
import org.julakali.chargeahead.shared.data.createHttpClient
import org.julakali.chargeahead.shared.data.pruneCache
import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.CorridorPlanning
import org.julakali.chargeahead.shared.domain.DataSourceDirectory
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.usecases.LoadDataSourcesInteractor
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.PlannedTripStorage
import org.julakali.chargeahead.shared.domain.usecases.ChargeNowObserver
import org.julakali.chargeahead.shared.domain.usecases.ChargeStopsObserver
import org.julakali.chargeahead.shared.domain.usecases.DestinationSearchObserver
import org.julakali.chargeahead.shared.domain.usecases.MapChargersObserver
import org.julakali.chargeahead.shared.domain.usecases.PlanTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.RefreshChargeNowInteractor
import org.julakali.chargeahead.shared.domain.usecases.RefreshChargeStopsInteractor
import org.julakali.chargeahead.shared.domain.usecases.LiveConnectorsObserver
import org.julakali.chargeahead.shared.domain.usecases.RefreshChargerAvailabilityInteractor
import org.julakali.chargeahead.shared.domain.usecases.RefreshLiveConnectorsInteractor
import org.julakali.chargeahead.shared.domain.usecases.RefreshNetworksInteractor
import org.julakali.chargeahead.shared.domain.usecases.RefreshMapChargersInteractor
import org.julakali.chargeahead.shared.domain.usecases.RemoveSavedRouteInteractor
import org.julakali.chargeahead.shared.domain.usecases.ReplanWithArrivalSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.RemoveVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.RenameSavedRouteInteractor
import org.julakali.chargeahead.shared.domain.usecases.SelectVehicleInteractor
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.usecases.SaveRouteInteractor
import org.julakali.chargeahead.shared.domain.usecases.ToggleSavedRouteInteractor
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripStore
import org.julakali.chargeahead.shared.domain.usecases.UpdateArrivalSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateChargeFiltersInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateNetworksInteractor
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * The data graph behind both features, declared once for both platforms.
 *
 * The platform module supplies [LocationSource] (the phone's),
 * [org.julakali.chargeahead.shared.db.DatabaseFactory], [SettingsStore] and
 * [BackendConfig]. Everything here is one instance per process.
 */
fun chargeStopsModule(): Module = module {
    single<TimeProvider> { TimeProvider { currentTimeMillis() } }

    single<HttpClient> { createHttpClient() } withOptions { onClose { it?.close() } }
    single<ChargeSiteDatabase> { createChargeSiteDatabase(get()) }

    single<SiteRepository> {
        val backend = get<BackendConfig>()
        MergingSiteRepository(
            listOf(
                TiledSiteRepository(
                    source = BackendChargeSiteSource(get(), backend.baseUrl, backend.token),
                    database = get(),
                    time = get(),
                ),
            ),
        )
    }

    single<RouteEngine> {
        val backend = get<BackendConfig>()
        BackendRouteEngine(get(), backend.baseUrl, backend.token)
    }

    single<Geocoder> {
        val backend = get<BackendConfig>()
        BackendGeocoder(get(), backend.baseUrl, backend.token)
    }

    single<TripPlanning> { TripPlanner(get(), get()) }
    single<PlannedTripStorage> { RoomTripStorage(get()) }
    single { TripStore(get()) }
    single<CorridorPlanning> { CorridorPlanner() }
    single<ChargePointStatusRepository> {
        val backend = get<BackendConfig>()
        CachingChargePointStatusRepository(BackendChargePointStatusSource(get(), backend.baseUrl, backend.token), get())
    }
    single<DataSourceDirectory> {
        val backend = get<BackendConfig>()
        BackendDataSourceDirectory(get(), backend.baseUrl, backend.token)
    }
    factory { LoadDataSourcesInteractor(get()) }
    factory { MapChargersObserver(get(), get(), get()) }
    factory { RefreshMapChargersInteractor(get(), get()) }
    factory { RefreshChargerAvailabilityInteractor(get(), get(), get()) }
    factory { LiveConnectorsObserver(get()) }
    factory { RefreshLiveConnectorsInteractor(get()) }
    factory { ChargeNowObserver(get(), get()) }
    factory { RefreshChargeNowInteractor(get(), get()) }
    factory { DestinationSearchObserver(get(), get()) }
    factory { PlanTripInteractor(get(), get(), get()) }
    factory { UpdateArrivalSocInteractor(get()) }
    factory { ReplanWithArrivalSocInteractor(get(), get(), get()) }
    factory { SaveRouteInteractor(get()) }
    factory { ToggleSavedRouteInteractor(get()) }
    factory { RenameSavedRouteInteractor(get()) }
    factory { RemoveSavedRouteInteractor(get()) }
    factory { SelectVehicleInteractor(get()) }
    factory { RemoveVehicleInteractor(get()) }
    factory { UpdateManualSocInteractor(get()) }
    factory { UpdateChargeFiltersInteractor(get()) }
    factory { UpdateNetworksInteractor(get()) }
    factory { ChargeStopsObserver(get(), get(), get(), get(), get()) }
    factory { RefreshChargeStopsInteractor(get(), get()) }
    single<NetworkRepository> {
        val backend = get<BackendConfig>()
        RoomNetworkRepository(BackendNetworkListSource(get(), backend.baseUrl, backend.token), get())
    }
    factory { RefreshNetworksInteractor(get()) }

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
    val refreshNetworks = get<RefreshNetworksInteractor>()
    val tripStore = get<TripStore>()
    return ChargeStopsFeature(
        locationSource = locationSource,
        socSource = CombinedSoCSource(
            manual = ManualSoCSource(settingsStore, time),
            hardware = hardwareSoCSource,
        ),
        onStart = {
            runCatching {
                val keys = settingsStore.networks.first().preferredOperators
                pruneCache(database, keys, time.nowMillis(), TiledSiteRepository.DEFAULT_TTL_MILLIS)
            }
            runCatching { tripStore.restore() }
            refreshNetworks(Unit)
        },
    )
}
