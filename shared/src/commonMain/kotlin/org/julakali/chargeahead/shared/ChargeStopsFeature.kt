package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.core.ChargeStopPlanner
import org.julakali.chargeahead.shared.core.CorridorRouteProvider
import org.julakali.chargeahead.shared.core.CourseTracker
import org.julakali.chargeahead.shared.core.RoutedRouteProvider
import org.julakali.chargeahead.shared.core.RefreshPolicy
import org.julakali.chargeahead.shared.core.RangeCalculator
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.RouteEngine
import org.julakali.chargeahead.shared.domain.RouteProvider
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.VehicleProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.julakali.chargeahead.shared.ChargeStopsState.RouteStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Single source of state for both car UIs.
 *
 * Pipeline: location -> course -> corridor -> source -> sector filter ->
 * sorted by distance.
 *
 * Lifecycle: [start] begins listening, [close] ends it for good.
 */
class ChargeStopsFeature(
    private val locationSource: LocationSource,
    private val repository: SiteRepository,
    private val routeProvider: RouteProvider = CorridorRouteProvider(),
    private val refreshPolicy: RefreshPolicy = RefreshPolicy(),
    private val routeEngine: RouteEngine? = null,
    private val routeBufferKm: Double = RoutedRouteProvider.DEFAULT_BUFFER_KM,
    private val settingsStore: SettingsStore? = null,
    private val socSource: SoCSource? = null,
    private val reserveSocPercent: Double = DEFAULT_RESERVE_SOC_PERCENT,
    private val isDemo: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Called by [close]. */
    private val onClose: () -> Unit = {},
    /** A one-shot task run on the feature's own scope at creation. */
    private val onStart: (suspend () -> Unit)? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        onStart?.let { task -> scope.launch { task() } }
    }
    private val courseTracker = CourseTracker()

    // Prevents the location loop and refresh() from computing concurrently.
    private val recomputeMutex = Mutex()

    // Guards the course tracker and the last-fix fields against locate().
    // Always taken before recomputeMutex, never the other way round.
    private val fixMutex = Mutex()

    private val mutableState = MutableStateFlow(ChargeStopsState(isDemo = isDemo))
    val state: StateFlow<ChargeStopsState> = mutableState.asStateFlow()

    private val mutableStops = MutableStateFlow<List<ChargeStop>>(emptyList())

    /** Contractual shorthand for [state] for callers that only care about the list. */
    val stops: StateFlow<List<ChargeStop>> = mutableStops.asStateFlow()

    /** Snapshot of the state, for callers without Flow support (Swift). */
    val currentState: ChargeStopsState get() = mutableState.value

    private val mutableFix = MutableStateFlow<Fix?>(null)

    /** Last location fix. */
    val currentFix: StateFlow<Fix?> = mutableFix.asStateFlow()

    private val mutableEnergy = MutableStateFlow<EnergyState?>(null)

    /** Latest combined charge state: the car's measurement when it delivers one, otherwise the manual entry. */
    val currentEnergy: StateFlow<EnergyState?> = mutableEnergy.asStateFlow()

    private var locationJob: Job? = null
    private var sensorJob: Job? = null
    private var planningJob: Job? = null

    // Whether the running location collector feeds recompute() or only the raw fix.
    private var computesStops = false
    private var latestFix: Fix? = null
    private var lastComputedFix: Fix? = null
    private var recomputeOnNextFix = false

    // Fed from the flows because recompute() needs synchronous access.
    private var vehicle: VehicleProfile? = null
    private var energy: EnergyState? = null

    // The route is computed once per destination, not on every location update.
    private var destination: Destination? = null
    private var routedProvider: RoutedRouteProvider? = null
    private var networks: NetworkPreferences = NetworkPreferences()

    /**
     * Starts the full pipeline: location updates, charge state, and the
     * computed corridor list. Idempotent; upgrades a running [startSensors].
     */
    fun start() {
        startSensorCollectors()
        startPlanningCollectors()
        startLocation(computeStops = true)
    }

    /**
     * Tracks location and charge state without computing charging stops.
     * Never displaces a running full pipeline.
     */
    fun startSensors() {
        startSensorCollectors()
        startLocation(computeStops = false)
    }

    /**
     * Asks the location source for a fix right now instead of waiting for the
     * stream's next update.
     */
    fun locate() {
        scope.launch {
            val fix = try {
                locationSource.currentFix()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                logWarning("Could not read the current location", error)
                null
            }
            fix?.let { onFix(it) }
        }
    }

    private fun startLocation(computeStops: Boolean) {
        if (locationJob?.isActive == true) {
            // Only the upgrade to the computing stream replaces a running collector.
            if (!computeStops || computesStops) return
            locationJob?.cancel()
        }

        computesStops = computeStops
        locationJob = scope.launch {
            locationSource.updates
                .catch { error ->
                    if (computeStops) {
                        // Permission missing or location services off; the last list stays.
                        publish(
                            mutableState.value.copy(
                                phase = ChargeStopsState.Phase.FAILED,
                                failure = ChargeStopsState.FailureReason.LOCATION_UNAVAILABLE,
                            ),
                        )
                    } else {
                        logWarning("Location stream ended", error)
                    }
                }
                .collect(::onFix)
        }
    }

    private fun startSensorCollectors() {
        if (sensorJob?.isActive == true) return
        sensorJob = scope.launch {
            socSource?.energy?.collect { updated ->
                energy = updated
                mutableEnergy.value = updated
                if (computesStops) recomputeLatest()
            }
        }
    }

    private fun startPlanningCollectors() {
        if (planningJob?.isActive == true) return
        planningJob = scope.launch {
            launch {
                // Range changes must take effect immediately, not at the next location update.
                settingsStore?.vehicle?.collect { updated ->
                    vehicle = updated
                    recomputeLatest()
                }
            }

            launch {
                settingsStore?.destination?.collect(::onDestinationChanged)
            }

            launch {
                settingsStore?.networks?.collect { updated ->
                    networks = updated
                    recomputeLatest()
                }
            }
        }
    }

    /** Sets the destination; `null` switches back to searching along the direction of travel. */
    suspend fun setDestination(destination: Destination?) {
        settingsStore?.setDestination(destination)
    }

    private suspend fun onDestinationChanged(updated: Destination?) {
        destination = updated
        routedProvider = null

        if (updated == null) {
            publish(mutableState.value.copy(destination = null, routeStatus = RouteStatus.NONE))
            recomputeLatest()
            return
        }

        publish(mutableState.value.copy(destination = updated, routeStatus = RouteStatus.CALCULATING))
        ensureRoute()
        recomputeLatest()
    }

    /**
     * Computes the route once per destination, if needed and possible.
     * Without a location yet, it is deferred to the first fix.
     */
    private suspend fun ensureRoute() {
        val target = destination ?: return
        if (routedProvider != null) return

        val engine = routeEngine
        val from = latestFix?.position
        if (engine == null || from == null) {
            publish(mutableState.value.copy(routeStatus = RouteStatus.UNAVAILABLE))
            return
        }

        val route = try {
            engine.route(from, target.position)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logWarning("Could not compute route to ${target.name}", error)
            null
        }

        if (route == null) {
            // The corridor keeps supplying results.
            publish(mutableState.value.copy(routeStatus = RouteStatus.UNAVAILABLE))
            return
        }

        routedProvider = RoutedRouteProvider(
            route = route,
            bufferKm = routeBufferKm,
            fallback = routeProvider,
        )
        publish(mutableState.value.copy(routeStatus = RouteStatus.ACTIVE))
    }

    /** Recomputes using the last known location, if there is one. */
    private suspend fun recomputeLatest() {
        latestFix?.let { recompute(it) }
    }

    /**
     * Range from vehicle profile and charge state — or the fallback value as
     * long as either is missing.
     */
    private fun currentRangeKm(): Double {
        val profile = vehicle ?: return FALLBACK_RANGE_KM
        val state = energy ?: return FALLBACK_RANGE_KM
        val range = RangeCalculator.rangeKm(profile, state.socPercent, reserveSocPercent)

        // Below the reserve, keep searching; classification says nothing is reachable.
        return range.coerceAtLeast(MIN_SEARCH_RANGE_KM)
    }

    /**
     * Forces a recomputation with freshly fetched data. As long as there is no
     * location yet, it is deferred to the first fix.
     */
    fun refresh() {
        val fix = latestFix
        if (fix == null) {
            recomputeOnNextFix = true
            return
        }
        scope.launch {
            repository.invalidate()
            recompute(fix)
        }
    }

    /** Ends processing for good. The instance is unusable afterward. */
    fun close() {
        scope.cancel()
        locationJob = null
        sensorJob = null
        planningJob = null
        onClose()
    }

    private suspend fun onFix(rawFix: Fix) = fixMutex.withLock {
        val fix = courseTracker.update(rawFix)
        val hadNoFix = latestFix == null
        latestFix = fix
        mutableFix.value = fix

        // Published independently of a successful recompute.
        publishPosition(fix.position)

        if (!computesStops) return

        // A destination might have been set before the first location arrived.
        if (hadNoFix) ensureRoute()

        val forced = recomputeOnNextFix
        if (!forced && !refreshPolicy.shouldRecompute(lastComputedFix, fix)) return
        recomputeOnNextFix = false

        recompute(fix)
    }

    private suspend fun recompute(fix: Fix) = recomputeMutex.withLock {
        lastComputedFix = fix

        val routed = routedProvider
        val area = (routed ?: routeProvider).searchArea(fix, currentRangeKm())
        val routeAhead = routed?.progressAt(fix)
        publish(
            mutableState.value.copy(
                phase = ChargeStopsState.Phase.LOADING,
                failure = null,
            ),
        )

        try {
            val sites = repository.load(area, networks.selectedNetworks())
            publish(
                ChargeStopsState(
                    stops = ChargeStopPlanner.plan(
                        area = area,
                        sites = sites,
                        vehicle = vehicle,
                        energy = energy,
                        reserveSocPercent = reserveSocPercent,
                        networks = networks,
                        routeAhead = routeAhead,
                    ),
                    phase = ChargeStopsState.Phase.READY,
                    failure = null,
                    isDemo = isDemo,
                    socSource = energy?.source,
                    destination = destination,
                    routeStatus = mutableState.value.routeStatus,
                    networkFilterActive = networks.isActive,
                    position = fix.position,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logWarning("Failed to load charging stations", error)
            // The old list stays in place.
            publish(
                mutableState.value.copy(
                    phase = ChargeStopsState.Phase.FAILED,
                    failure = ChargeStopsState.FailureReason.SITES_UNAVAILABLE,
                ),
            )
        }
    }

    /** Runs outside [recomputeMutex], hence [MutableStateFlow.update]. */
    private fun publishPosition(position: LatLon) {
        mutableState.update { it.copy(position = position) }
    }

    private fun publish(next: ChargeStopsState) {
        mutableState.value = next
        mutableStops.value = next.stops
    }

    companion object {
        /** Search radius as long as vehicle profile or charge state is missing; above the corridor cap. */
        const val FALLBACK_RANGE_KM = 300.0

        /** Smallest search radius, even with an empty battery. */
        const val MIN_SEARCH_RANGE_KM = 25.0
    }
}
