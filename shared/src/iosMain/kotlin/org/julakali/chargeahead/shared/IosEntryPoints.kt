package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.data.CoreLocationSource
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.ChargeNowResult
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.PlanTrip
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import org.julakali.chargeahead.shared.settings.createSettingsDataStore
import org.julakali.chargeahead.shared.ui.ChargeNowUiState
import org.julakali.chargeahead.shared.ui.ChargeNowViewModel
import org.julakali.chargeahead.shared.ui.CorridorViewModel
import org.julakali.chargeahead.shared.ui.PlanSheetUiState
import org.julakali.chargeahead.shared.ui.PlanSheetViewModel
import org.julakali.chargeahead.shared.ui.ViewModelHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The entry points for Swift: they resolve from the Koin graph on Swift's
 * behalf, and [ChargeStopsWatcher] turns a `StateFlow` into a plain callback.
 */
fun createSettingsStore(): SettingsStore =
    PersistentSettingsStore(createSettingsDataStore())

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

/** The corridor list for Swift: every state change, on the main thread, until [stop]. */
class ChargeStopsWatcher(feature: ChargeStopsFeature) {

    private val koin: Koin = requireNotNull(graph) {
        "No graph yet — call createChargeStopsFeature first"
    }
    private val viewModels = ViewModelHost()
    private val viewModel = viewModels.get { CorridorViewModel(feature, koin.get(), koin.get()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Snapshot of the state, for callers without Flow support. */
    val currentState: ChargeStopsState get() = viewModel.uiState.value

    fun start(onChange: (ChargeStopsState) -> Unit) {
        scope.launch { viewModel.uiState.collect(onChange) }
    }

    fun refresh() {
        viewModel.onRefresh()
    }

    fun stop() {
        scope.cancel()
        viewModels.clear()
    }
}

/** [TripPlanResult], flattened for Swift. */
data class TripPlanOutcome(
    val plan: TripPlan?,
    val failure: TripPlanFailure?,
) {
    enum class TripPlanFailure { NO_VEHICLE, NO_ROUTE, NO_CHARGER_IN_REACH }
}

/** The phone planning flows as plain callbacks on the main thread; [close] ends them. */
class PlanningBridge(feature: ChargeStopsFeature) {

    private val koin: Koin = requireNotNull(graph) {
        "No graph yet — call createChargeStopsFeature first"
    }
    private val planTrip: PlanTrip = koin.get()
    private val viewModels = ViewModelHost()
    private val chargeNowViewModel = viewModels.get { ChargeNowViewModel(feature, koin.get(), koin.get()) }
    private val planSheetViewModel = viewModels.get { PlanSheetViewModel(feature, koin.get(), koin.get()) }
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

    /** Ranks from the feature's current position and reports every change until [close]. */
    fun chargeNow(onResult: (ChargeNowResult) -> Unit) {
        chargeNowJob?.cancel()
        chargeNowJob = scope.launch {
            chargeNowViewModel.uiState
                .mapNotNull { (it as? ChargeNowUiState.Ready)?.result }
                .collect(onResult)
        }
        chargeNowViewModel.onSheetOpened()
    }

    /** Reports the places found for the latest [searchDestinations] query until [close]. */
    fun watchDestinationSearch(onChange: (List<Place>) -> Unit) {
        searchJob?.cancel()
        searchJob = scope.launch {
            planSheetViewModel.uiState
                .filter { !it.searching }
                .map { it.results.orEmpty() }
                .distinctUntilChanged()
                .collect(onChange)
        }
    }

    /** Debounced; queries shorter than [PlanSheetUiState.MIN_QUERY_LENGTH] find nothing. */
    fun searchDestinations(query: String) {
        planSheetViewModel.onQueryChanged(query)
    }

    fun close() {
        scope.cancel()
        viewModels.clear()
    }
}
