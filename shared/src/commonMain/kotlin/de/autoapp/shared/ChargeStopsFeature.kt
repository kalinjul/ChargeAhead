package de.autoapp.shared

import de.autoapp.shared.core.ChargeStopPlanner
import de.autoapp.shared.core.CorridorRouteProvider
import de.autoapp.shared.core.CourseTracker
import de.autoapp.shared.core.RoutedRouteProvider
import de.autoapp.shared.core.RefreshPolicy
import de.autoapp.shared.core.RangeCalculator
import de.autoapp.shared.domain.ChargeStop
import de.autoapp.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.EnergyState
import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LocationSource
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.data.OperatorCatalog
import de.autoapp.shared.domain.OperatorOption
import de.autoapp.shared.domain.OperatorOptions
import de.autoapp.shared.domain.Geocoder
import de.autoapp.shared.domain.Place
import de.autoapp.shared.domain.RouteEngine
import de.autoapp.shared.domain.RouteProvider
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.SoCSource
import de.autoapp.shared.domain.VehicleProfile
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import de.autoapp.shared.ChargeStopsState.RouteStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Single source of state for both car UIs (see AGENTS.md).
 *
 * The pipeline is the same as ARCHITECTURE.md section 5.3: location -> course
 * -> corridor -> source -> sector filter -> sorted by distance. Android and
 * iOS only plug in the platform-specific parts — [LocationSource] and the
 * concrete source behind [SiteRepository]; everything in between is shared.
 *
 * Lifecycle: [start] begins listening, [close] ends it for good. The instance
 * is owned by whichever UI created it — the `Screen` in Android Auto, the
 * scene delegate in CarPlay.
 */
class ChargeStopsFeature(
    private val locationSource: LocationSource,
    private val repository: SiteRepository,
    private val routeProvider: RouteProvider = CorridorRouteProvider(),
    private val refreshPolicy: RefreshPolicy = RefreshPolicy(),
    private val routeEngine: RouteEngine? = null,
    private val geocoder: Geocoder? = null,
    private val routeBufferKm: Double = RoutedRouteProvider.DEFAULT_BUFFER_KM,
    private val operatorCatalog: OperatorCatalog? = null,
    private val settingsStore: SettingsStore? = null,
    private val socSource: SoCSource? = null,
    private val reserveSocPercent: Double = DEFAULT_RESERVE_SOC_PERCENT,
    private val isDemo: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Called by [close] — this is where the creator releases its own resources. */
    private val onClose: () -> Unit = {},
    /**
     * The phone's planning flows, assembled by the factory on the same
     * repository and settings. `null` in tests that assemble by hand and
     * don't need it.
     */
    val planning: PlanningFeature? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val courseTracker = CourseTracker()

    // Prevents the location loop and an externally triggered refresh() from
    // computing concurrently and racing each other when publishing.
    private val recomputeMutex = Mutex()

    // isDemo is set in the initial state already, not only once the first list
    // arrives: otherwise the demo notice would appear late in the UI and the
    // driver would briefly see the header without it.
    private val mutableState = MutableStateFlow(ChargeStopsState(isDemo = isDemo))
    val state: StateFlow<ChargeStopsState> = mutableState.asStateFlow()

    private val mutableStops = MutableStateFlow<List<ChargeStop>>(emptyList())

    /** Contractual shorthand for [state] for callers that only care about the list. */
    val stops: StateFlow<List<ChargeStop>> = mutableStops.asStateFlow()

    /**
     * Snapshot of the state, for callers without Flow support — from Swift,
     * `state.value` without SKIE is only available as `Any?` and would need
     * casting back.
     */
    val currentState: ChargeStopsState get() = mutableState.value

    private val mutableFix = MutableStateFlow<Fix?>(null)

    /** Last location fix — for screens that plan on demand instead of following the stops list. */
    val currentFix: StateFlow<Fix?> = mutableFix.asStateFlow()

    private val mutableEnergy = MutableStateFlow<EnergyState?>(null)

    /** Latest combined charge state: the car's measurement when it delivers one, otherwise the manual entry. */
    val currentEnergy: StateFlow<EnergyState?> = mutableEnergy.asStateFlow()

    private var locationJob: Job? = null
    private var settingsJob: Job? = null
    private var latestFix: Fix? = null
    private var lastComputedFix: Fix? = null
    private var recomputeOnNextFix = false

    // Last known vehicle and charge state. Fed from the flows because
    // recompute() needs to access them synchronously.
    private var vehicle: VehicleProfile? = null
    private var energy: EnergyState? = null

    // The set destination and the route computed from it once. The provider
    // stays in place as long as the destination is set — the route is not
    // recomputed on every location update, only trimmed from the front.
    private var destination: Destination? = null
    private var routedProvider: RouteProvider? = null
    private var networks: NetworkPreferences = NetworkPreferences()

    /** Starts processing location updates. Calling it more than once is a no-op. */
    fun start() {
        if (locationJob?.isActive == true) return
        locationJob = scope.launch {
            locationSource.updates
                .catch {
                    // The location stream terminates when permission is missing
                    // or location services are off. The last known list stays,
                    // and the reason is surfaced alongside it.
                    publish(
                        mutableState.value.copy(
                            phase = ChargeStopsState.Phase.FAILED,
                            failure = ChargeStopsState.FailureReason.LOCATION_UNAVAILABLE,
                        ),
                    )
                }
                .collect(::onFix)
        }

        settingsJob = scope.launch {
            // A changed vehicle or a newly typed-in charge level changes range,
            // which affects both classification and corridor size. Both must
            // take effect immediately, not only at the next location update —
            // that could be two kilometers away.
            settingsStore?.vehicle?.collect { updated ->
                vehicle = updated
                recomputeLatest()
            }
        }

        scope.launch {
            socSource?.energy?.collect { updated ->
                energy = updated
                mutableEnergy.value = updated
                recomputeLatest()
            }
        }

        scope.launch {
            settingsStore?.destination?.collect(::onDestinationChanged)
        }

        scope.launch {
            settingsStore?.networks?.collect { updated ->
                networks = updated
                recomputeLatest()
            }
        }

        scope.launch {
            val cached = operatorCatalog?.options().orEmpty()
            if (cached.isEmpty()) return@launch
            recomputeMutex.withLock {
                // A live recompute may already have won the race; its list is fresher.
                if (mutableState.value.availableOperators.isEmpty()) {
                    publish(mutableState.value.copy(availableOperators = cached))
                }
            }
        }
    }

    /**
     * Tracks location and charge state without computing charging stops — for
     * the car UI, which plans on demand and has no corridor list to feed.
     * Use either [start] or [startSensors] per instance, not both.
     */
    fun startSensors() {
        if (locationJob?.isActive == true) return
        locationJob = scope.launch {
            locationSource.updates
                // The fix stays null; screens keep saying they are waiting for
                // a location instead of planning from a stale one.
                .catch { error -> logWarning("Location stream ended", error) }
                .collect { raw ->
                    val fix = courseTracker.update(raw)
                    latestFix = fix
                    mutableFix.value = fix
                }
        }

        scope.launch {
            socSource?.energy?.collect { updated ->
                energy = updated
                mutableEnergy.value = updated
            }
        }
    }

    /** Searches for places matching the typed text. Empty list if no geocoder is configured. */
    suspend fun searchDestinations(query: String): List<Place> =
        geocoder?.search(query, near = latestFix?.position).orEmpty()

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
     * Computes the route, if needed and possible.
     *
     * Exactly one call per destination — not per location update. Without a
     * location yet, it is deferred to the first fix; without a routing
     * service, it falls back to the corridor.
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
            // No reason to empty the list: the corridor keeps supplying results.
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

        // Below the reserve, the corridor would shrink to zero kilometers and
        // the list would be empty — right when the driver needs it most.
        // Searching continues regardless; classification will then simply say
        // that nothing is reachable.
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
        settingsJob = null
        onClose()
    }

    private suspend fun onFix(rawFix: Fix) {
        val fix = courseTracker.update(rawFix)
        val hadNoFix = latestFix == null
        latestFix = fix
        mutableFix.value = fix

        // A destination might have been set before the first location arrived.
        if (hadNoFix) ensureRoute()

        val forced = recomputeOnNextFix
        if (!forced && !refreshPolicy.shouldRecompute(lastComputedFix, fix)) return
        recomputeOnNextFix = false

        recompute(fix)
    }

    private suspend fun recompute(fix: Fix) = recomputeMutex.withLock {
        // Set only here: if the query below failed before this point, distance
        // would end up measured from a fix that was never actually computed,
        // delaying the next update by up to 2 km.
        lastComputedFix = fix

        val area = (routedProvider ?: routeProvider).searchArea(fix, currentRangeKm())
        publish(
            mutableState.value.copy(
                phase = ChargeStopsState.Phase.LOADING,
                failure = null,
            ),
        )

        try {
            val sites = repository.sitesIn(area)
            publish(
                ChargeStopsState(
                    stops = ChargeStopPlanner.plan(
                        area = area,
                        sites = sites,
                        vehicle = vehicle,
                        energy = energy,
                        reserveSocPercent = reserveSocPercent,
                        networks = networks,
                    ),
                    phase = ChargeStopsState.Phase.READY,
                    failure = null,
                    isDemo = isDemo,
                    socSource = energy?.source,
                    destination = destination,
                    routeStatus = mutableState.value.routeStatus,
                    // Derived from the unfiltered sites: otherwise the filter
                    // would hide the very options needed to undo it.
                    availableOperators = sites.toOperatorOptions(),
                    networkFilterActive = networks.isActive,
                    position = fix.position,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logWarning("Failed to load charging stations", error)
            // The old list is deliberately left in place — see ChargeStopsState.
            publish(
                mutableState.value.copy(
                    phase = ChargeStopsState.Phase.FAILED,
                    failure = ChargeStopsState.FailureReason.SITES_UNAVAILABLE,
                ),
            )
        }
    }

    private fun List<de.autoapp.shared.domain.ChargeSite>.toOperatorOptions(): List<OperatorOption> =
        OperatorOptions.fromNames(map { it.operator })

    private fun publish(next: ChargeStopsState) {
        mutableState.value = next
        mutableStops.value = next.stops
    }

    companion object {
        /**
         * Search radius as long as vehicle profile or charge state is missing.
         *
         * Chosen above the corridor's 150 km cap so the radius lands exactly
         * there instead of depending on a made-up range. This is not a range
         * substitute — without a profile there is no classification at all.
         */
        const val FALLBACK_RANGE_KM = 300.0

        /**
         * Smallest search radius, even with an empty battery. Otherwise the
         * list would be empty right when the driver needs it most.
         */
        const val MIN_SEARCH_RANGE_KM = 25.0
    }
}
