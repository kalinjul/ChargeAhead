package org.julakali.chargeahead.shared

import androidx.lifecycle.SavedStateHandle
import org.julakali.chargeahead.shared.data.CoreLocationSource
import org.julakali.chargeahead.shared.data.DataStoreTripStorage
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.usecases.PlanTripInteractor
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.TripStorage
import org.julakali.chargeahead.shared.settings.createSettingsDataStore
import org.julakali.chargeahead.shared.settings.createTripDataStore
import org.julakali.chargeahead.shared.settings.settingsModule
import org.julakali.chargeahead.shared.ui.ChargeNowViewModel
import org.julakali.chargeahead.shared.ui.CorridorViewModel
import org.julakali.chargeahead.shared.ui.HomeViewModel
import org.julakali.chargeahead.shared.ui.SearchViewModel
import org.julakali.chargeahead.shared.ui.ViewModelHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.core.Koin
import org.koin.core.scope.Scope
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.core.parameter.parametersOf
import org.julakali.chargeahead.shared.ui.sharedUiModule
import org.julakali.chargeahead.shared.ui.phoneSession
import org.julakali.chargeahead.shared.domain.usecases.StartAppInteractor
import org.julakali.chargeahead.shared.domain.invoke

private var graph: Koin? = null

/**
 * Builds the process-wide graph, once, from the app delegate's launch.
 * @throws IllegalArgumentException when the backend is not configured; the app cannot run without it.
 */
fun startChargeAhead(backendBaseUrl: String?, backendToken: String?) {
    check(graph == null) { "ChargeAhead is already started" }
    val backend = requireNotNull(BackendConfig.of(backendBaseUrl, backendToken)) {
        "ChargeAheadBaseUrl and ChargeAheadToken must be set in Info.plist"
    }
    val koin = buildGraph(backend)
    graph = koin
    koin.get<CoroutineScope>(AppScope).launch { koin.get<StartAppInteractor>()() }
}

private fun requireGraph(): Koin = checkNotNull(graph) { "No graph yet — call startChargeAhead first" }

private fun buildGraph(backend: BackendConfig): Koin =
    koinApplication {
        modules(
            settingsModule { createSettingsDataStore(dataStoreScope()) },
            module {
                single<TripStorage> { DataStoreTripStorage(createTripDataStore(dataStoreScope()), legacy = get()) }
                single<LocationSource> { CoreLocationSource() }
                single { DatabaseFactory() }
                single { backend }
            },
            chargeStopsModule(),
            sharedUiModule(),
        )
    }.koin

private fun Scope.dataStoreScope(): CoroutineScope = get<CoroutineScope>(AppScope) + get<AppCoroutineDispatchers>().io

/** Each caller owns its feature; the data graph beneath is shared. */
fun createChargeStopsFeature(): ChargeStopsFeature =
    requireGraph().get(SessionFeature) { parametersOf(CoreLocationSource(), null) }

/** The corridor list for CarPlay: every state change, on the main thread, until [stop]. */
class ChargeStopsWatcher(feature: ChargeStopsFeature) {

    private val koin: Koin = requireGraph()
    private val session = koin.phoneSession(feature)
    private val viewModels = ViewModelHost()
    private val viewModel = viewModels.get { session.get<CorridorViewModel>() }
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
        session.close()
    }
}

/** [TripPlanResult], flattened for Swift. */
data class TripPlanOutcome(
    val plan: TripPlan?,
    val failure: TripPlanFailure?,
) {
    enum class TripPlanFailure { NO_VEHICLE, NO_ROUTE, NO_CONNECTION, NO_CHARGER_IN_REACH }
}

/**
 * The phone screens' ViewModels over [feature]. A SwiftUI owner holds one per
 * screen or sheet and calls [clear] when it goes away.
 */
class PhoneViewModels(private val feature: ChargeStopsFeature) {

    private val koin: Koin = requireGraph()
    private val session = koin.phoneSession(feature)
    private val host = ViewModelHost()

    fun home(): HomeViewModel = host.get { session.get<HomeViewModel>() }

    fun chargeNow(): ChargeNowViewModel = host.get { session.get<ChargeNowViewModel>() }

    fun search(): SearchViewModel = host.get { session.get<SearchViewModel> { parametersOf(SavedStateHandle()) } }

    fun clear() {
        host.clear()
        session.close()
    }
}

/** Plans a trip for Swift, which sees [planTrip] as `async`. */
class TripPlanner {

    private val planTrip: PlanTripInteractor = requireGraph().get()

    suspend fun planTrip(from: LatLon, destination: Destination): TripPlanOutcome =
        planTrip(PlanTripInteractor.Params(from, destination)).fold(
            onSuccess = { result ->
                when (result) {
                    is TripPlanResult.Planned -> TripPlanOutcome(result.plan, null)
                    is TripPlanResult.NoVehicle -> TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_VEHICLE)
                    is TripPlanResult.NoRoute -> TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_ROUTE)
                    is TripPlanResult.NoConnection -> TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_CONNECTION)
                    is TripPlanResult.NoChargerInReach ->
                        TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_CHARGER_IN_REACH)
                }
            },
            onFailure = { TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_ROUTE) },
        )
}
