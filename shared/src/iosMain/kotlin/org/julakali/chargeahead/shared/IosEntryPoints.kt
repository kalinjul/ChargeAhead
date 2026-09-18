package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.data.CoreLocationSource
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.ChargeNowResult
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.ObserveChargeNow
import org.julakali.chargeahead.shared.domain.ObserveDestinationSearch
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.PlanTrip
import org.julakali.chargeahead.shared.domain.RefreshChargeNow
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import org.julakali.chargeahead.shared.settings.UserDefaultsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The entry points for Swift: they resolve from the Koin graph on Swift's
 * behalf, and [ChargeStopsWatcher] turns a `StateFlow` into a plain callback.
 */
fun createSettingsStore(): SettingsStore =
    PersistentSettingsStore(UserDefaultsStorage())

/**
 * The process-wide graph, built on the first [createChargeStopsFeature] call;
 * the first call's values win.
 * TODO start Koin explicitly from Swift (#39)
 */
private var graph: Koin? = null

private fun graph(openChargeMapKey: String?, settingsStore: SettingsStore): Koin =
    graph ?: koinApplication {
        modules(
            module {
                single { settingsStore }
                single<LocationSource> { CoreLocationSource() }
                single { DatabaseFactory() }
                single { ChargeStopsConfig(openChargeMapKey, backend = null) }
            },
            chargeStopsModule(),
        )
    }.koin.also { graph = it }

/** @param settingsStore the same instance that also backs the settings view. */
fun createChargeStopsFeature(
    openChargeMapKey: String?,
    settingsStore: SettingsStore,
): ChargeStopsFeature =
    // Each caller owns its feature; the data graph beneath is shared.
    graph(openChargeMapKey, settingsStore).newChargeStopsFeature(locationSource = CoreLocationSource())

/** Reports every state change to Swift, on the main thread. */
class ChargeStopsWatcher(private val feature: ChargeStopsFeature) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start(onChange: (ChargeStopsState) -> Unit) {
        scope.launch { feature.state.collect(onChange) }
    }

    fun stop() {
        scope.cancel()
    }
}

/** [TripPlanResult], flattened for Swift. */
data class TripPlanOutcome(
    val plan: TripPlan?,
    val failure: TripPlanFailure?,
) {
    enum class TripPlanFailure { NO_VEHICLE, NO_ROUTE, NO_CHARGER_IN_REACH }
}

/**
 * The phone planning flows as plain callbacks on the main thread.
 *
 * @param feature unused; kept for the Swift call site until #39.
 */
class PlanningBridge(@Suppress("UNUSED_PARAMETER") feature: ChargeStopsFeature) {

    private val koin: Koin = requireNotNull(graph) {
        "No graph yet — call createChargeStopsFeature first"
    }
    private val planTrip: PlanTrip = koin.get()
    private val observeChargeNow: ObserveChargeNow = koin.get()
    private val refreshChargeNow: RefreshChargeNow = koin.get()
    private val observeDestinationSearch: ObserveDestinationSearch = koin.get()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var chargeNowJob: Job? = null
    private var searchJob: Job? = null

    fun planTrip(
        from: LatLon,
        destination: Destination,
        onResult: (TripPlanOutcome) -> Unit,
    ) {
        scope.launch {
            val outcome = planTrip(PlanTrip.Params(from, destination)).fold(
                onSuccess = { result ->
                    when (result) {
                        is TripPlanResult.Planned -> TripPlanOutcome(result.plan, null)
                        is TripPlanResult.NoVehicle -> TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_VEHICLE)
                        is TripPlanResult.NoRoute -> TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_ROUTE)
                        is TripPlanResult.NoChargerInReach ->
                            TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_CHARGER_IN_REACH)
                    }
                },
                onFailure = { TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_ROUTE) },
            )
            onResult(outcome)
        }
    }

    /** Reports the ranking around [position] on every change until [close]: the stored sites first, the refilled ones after. */
    fun chargeNow(position: LatLon, onResult: (ChargeNowResult) -> Unit) {
        observeChargeNow(ObserveChargeNow.Params(position))
        chargeNowJob?.cancel()
        chargeNowJob = scope.launch {
            // Undispatched, so the refill counts as running before the first ranking arrives.
            launch(start = CoroutineStart.UNDISPATCHED) { refreshChargeNow(RefreshChargeNow.Params(position)) }
            combine(observeChargeNow.flow.filterNotNull(), refreshChargeNow.inProgress) { result, refreshing ->
                result.takeUnless { it.isEmpty && refreshing }
            }
                .filterNotNull()
                .collect(onResult)
        }
    }

    /** Reports the places found for the latest [searchDestinations] query until [close]. */
    fun watchDestinationSearch(onChange: (List<Place>) -> Unit) {
        searchJob?.cancel()
        searchJob = scope.launch {
            observeDestinationSearch.flow
                .filter { !it.searching }
                .collect { onChange(it.results.orEmpty()) }
        }
    }

    /** Debounced; queries shorter than [ObserveDestinationSearch.MIN_QUERY_LENGTH] find nothing. */
    fun searchDestinations(query: String) {
        observeDestinationSearch(ObserveDestinationSearch.Params(query))
    }

    fun close() {
        scope.cancel()
    }
}
