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
import org.julakali.chargeahead.shared.data.RoomNetworkRepository
import org.julakali.chargeahead.shared.data.TiledSiteRepository
import org.julakali.chargeahead.shared.data.createHttpClient
import org.julakali.chargeahead.shared.data.pruneCache
import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.CorridorPlanning
import org.julakali.chargeahead.shared.domain.DataSourceDirectory
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.usecases.LoadDataSourcesInteractor
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.ChargePointStatusRepository
import org.julakali.chargeahead.shared.domain.NetworkRepository
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
import org.julakali.chargeahead.shared.domain.usecases.CommitTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.EndTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.DismissPlannedTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.ReplanCommittedTripInteractor
import org.julakali.chargeahead.shared.domain.usecases.ReplanWithArrivalSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.RemoveVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.SelectVehicleInteractor
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.TripPlanning
import org.julakali.chargeahead.shared.domain.TripRepository
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.usecases.UpdateArrivalSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateChargeFiltersInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateManualSocInteractor
import org.julakali.chargeahead.shared.domain.usecases.UpdateNetworksInteractor
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import org.julakali.chargeahead.shared.domain.usecases.StartAppInteractor
import org.julakali.chargeahead.shared.domain.usecases.VehiclePresetsObserver
import org.julakali.chargeahead.shared.domain.usecases.GarageObserver
import org.julakali.chargeahead.shared.domain.usecases.SelectableNetworksObserver
import org.julakali.chargeahead.shared.domain.SiteCache
import org.koin.core.qualifier.named
import org.koin.core.parameter.parametersOf

/**
 * The data graph behind both features, declared once for both platforms.
 *
 * The platform modules supply [LocationSource] (the phone's),
 * [org.julakali.chargeahead.shared.db.DatabaseFactory], the settings
 * repositories (`settingsModule`), [org.julakali.chargeahead.shared.domain.TripStorage]
 * and [BackendConfig].
 * Everything here is one instance per process.
 */
fun chargeStopsModule(): Module = module {
    single { AppCoroutineDispatchers(io = Dispatchers.IO, computation = Dispatchers.Default, main = Dispatchers.Main) }
    single<CoroutineScope>(AppScope) {
        CoroutineScope(SupervisorJob() + get<AppCoroutineDispatchers>().computation)
    } withOptions { onClose { it?.cancel() } }
    single<TimeProvider> { TimeProvider { currentTimeMillis() } }

    single<HttpClient> { createHttpClient(backend = get()) } withOptions { onClose { it?.close() } }
    single<ChargeSiteDatabase> { createChargeSiteDatabase(get(), get<AppCoroutineDispatchers>().io) }

    single<SiteRepository> {
        MergingSiteRepository(
            listOf(
                TiledSiteRepository(
                    source = BackendChargeSiteSource(get()),
                    database = get(),
                    time = get(),
                    scope = get(AppScope),
                ),
            ),
            computation = get<AppCoroutineDispatchers>().computation,
        )
    }

    single<RouteEngine> { BackendRouteEngine(get()) }

    single<Geocoder> { BackendGeocoder(get()) }

    single<TripPlanning> { TripPlanner(get(), get()) }
    single { TripRepository(get()) }
    single<CorridorPlanning> { CorridorPlanner() }
    single<ChargePointStatusRepository> { CachingChargePointStatusRepository(BackendChargePointStatusSource(get()), get()) }
    single<DataSourceDirectory> { BackendDataSourceDirectory(get()) }
    factory { LoadDataSourcesInteractor(get()) }
    factory { MapChargersObserver(get(), get(), get(), get()) }
    factory { RefreshMapChargersInteractor(get(), get()) }
    factory { RefreshChargerAvailabilityInteractor(get(), get(), get(), get()) }
    factory { LiveConnectorsObserver(get()) }
    factory { RefreshLiveConnectorsInteractor(get()) }
    factory { ChargeNowObserver(get(), get(), get()) }
    factory { RefreshChargeNowInteractor(get(), get()) }
    factory { DestinationSearchObserver(get(), get()) }
    factory { PlanTripInteractor(get(), get(), get(), get(), get(), get()) }
    factory { UpdateArrivalSocInteractor(get()) }
    factory { ReplanWithArrivalSocInteractor(get(), get(), get()) }
    factory { CommitTripInteractor(get(), get()) }
    factory { EndTripInteractor(get()) }
    factory { DismissPlannedTripInteractor(get()) }
    factory { ReplanCommittedTripInteractor(get(), get(), get(), get(), get(), get(), get()) }
    factory { SelectVehicleInteractor(get()) }
    factory { RemoveVehicleInteractor(get()) }
    factory { UpdateManualSocInteractor(get()) }
    factory { UpdateChargeFiltersInteractor(get()) }
    factory { UpdateNetworksInteractor(get()) }
    factory { ChargeStopsObserver(get(), get(), get(), get(), get(), get(), get()) }
    factory { RefreshChargeStopsInteractor(get(), get()) }
    factory { SelectableNetworksObserver(get(), get(), get()) }
    factory { GarageObserver(get<VehicleRepository>()) }
    factory { VehiclePresetsObserver(get<VehicleRepository>(), get()) }
    single<NetworkRepository> { RoomNetworkRepository(BackendNetworkListSource(get()), get()) }
    factory { RefreshNetworksInteractor(get()) }

    single<SiteCache> {
        SiteCache { keys -> pruneCache(get(), keys, get<TimeProvider>().nowMillis(), TiledSiteRepository.DEFAULT_TTL_MILLIS) }
    }
    factory { StartAppInteractor(get(), get(), get(), get()) }

    // A feature per session, on the graph's shared singletons; the caller owns and closes it.
    factory<ChargeStopsFeature>(SessionFeature) { (location: LocationSource, hardware: SoCSource?) ->
        ChargeStopsFeature(
            locationSource = location,
            socSource = CombinedSoCSource(manual = ManualSoCSource(get(), get()), hardware = hardware),
            parentScope = get(AppScope),
        )
    }
    // The phone's feature. Never closed.
    single<ChargeStopsFeature> { get(SessionFeature) { parametersOf(get<LocationSource>(), null) } }
}

/** A [ChargeStopsFeature] built for one session: `parametersOf(location, hardwareSoCSource)`. */
val SessionFeature = named("session-feature")
