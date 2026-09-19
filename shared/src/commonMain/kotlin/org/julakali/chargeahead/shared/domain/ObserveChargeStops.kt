package org.julakali.chargeahead.shared.domain

import org.julakali.chargeahead.shared.logWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The charge stops ahead: along the route to the destination, or in a
 * sector along the direction of travel. `null` until the first fix.
 *
 * Re-searches when [RefreshPolicy] says the vehicle has moved on enough, and
 * at once when vehicle, charge, networks or route change. Each new search
 * area is refilled from the network; the list comes from the store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveChargeStops(
    private val repository: SiteRepository,
    private val settings: SettingsStore,
    private val tripStore: TripStore,
    private val routeEngine: RouteEngine,
    private val planning: CorridorPlanning,
    private val refreshPolicy: RefreshPolicy = RefreshPolicy(),
) : SubjectInteractor<ObserveChargeStops.Params, ChargeStops?>() {

    /** The caller's own location and charge: the car's differ from the phone's. */
    data class Params(val fixes: Flow<Fix?>, val energy: Flow<EnergyState?>)

    private data class RouteState(val destination: Destination?, val route: Route?, val status: RouteStatus)

    private data class Inputs(
        val fix: Fix,
        val vehicle: VehicleProfile?,
        val energy: EnergyState?,
        val networks: NetworkPreferences,
        val route: RouteState,
    )

    override fun createObservable(params: Params): Flow<ChargeStops?> {
        val routes = settings.destination.distinctUntilChanged().flatMapLatest { routeTo(it, params.fixes) }
        return combine(
            params.fixes,
            settings.vehicle,
            params.energy,
            settings.networks,
            routes,
        ) { fix, vehicle, energy, networks, route ->
            fix?.let { Inputs(it, vehicle, energy, networks, route) }
        }
            // Compared with the last search, not the last fix: small moves add up.
            .distinctUntilChanged { searched, next ->
                searched != null && next != null &&
                    searched.copy(fix = next.fix) == next &&
                    !refreshPolicy.shouldRecompute(searched.fix, next.fix)
            }
            .flatMapLatest { inputs -> inputs?.let(::search) ?: flowOf(null) }
    }

    private fun routeTo(destination: Destination?, fixes: Flow<Fix?>): Flow<RouteState> {
        if (destination == null) return flowOf(RouteState(null, null, RouteStatus.NONE))
        // A planned trip to the destination brings its route along, so it's fetched only once.
        return tripStore.plan
            .map { plan -> plan?.route?.takeIf { plan.destination.position == destination.position } }
            .distinctUntilChanged()
            .flatMapLatest { planned ->
                if (planned != null) return@flatMapLatest flowOf(RouteState(destination, planned, RouteStatus.ACTIVE))
                flow {
                    emit(RouteState(destination, null, RouteStatus.CALCULATING))
                    val from = fixes.filterNotNull().first().position
                    val route = cancellableRunCatching { routeEngine.route(from, destination.position) }
                        .onFailure { logWarning("Could not compute route to ${destination.name}", it) }
                        .getOrNull()
                    // Without a route the corridor keeps supplying results.
                    emit(RouteState(destination, route, if (route != null) RouteStatus.ACTIVE else RouteStatus.UNAVAILABLE))
                }
            }
    }

    private fun search(inputs: Inputs): Flow<ChargeStops> = channelFlow {
        val corridor = planning.corridor(inputs.route.route)
        val area = corridor.searchArea(inputs.fix, inputs.vehicle, inputs.energy)
        val refill = MutableStateFlow(ChargeStops.Refill.RUNNING)
        launch {
            val result = cancellableRunCatching { repository.load(area, inputs.networks.selectedKeys()) }
            result.exceptionOrNull()?.let { logWarning("Failed to load charging stations", it) }
            refill.value = if (result.isSuccess) ChargeStops.Refill.DONE else ChargeStops.Refill.FAILED
        }
        val stops = repository.storedSitesIn(area).map { sites ->
            withContext(Dispatchers.Default) {
                corridor.stops(inputs.fix, area, sites, inputs.vehicle, inputs.energy, inputs.networks)
            }
        }
        combine(stops, refill) { list, refillState ->
            ChargeStops(
                stops = list,
                area = area,
                refill = refillState,
                destination = inputs.route.destination,
                routeStatus = inputs.route.status,
                networkFilterActive = inputs.networks.isActive,
                socSource = inputs.energy?.source,
            )
        }.collect { send(it) }
    }
}
