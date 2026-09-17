package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.data.CoreLocationSource
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import org.julakali.chargeahead.shared.settings.UserDefaultsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

/** [org.julakali.chargeahead.shared.core.TripPlanResult], flattened for Swift. */
data class TripPlanOutcome(
    val plan: org.julakali.chargeahead.shared.core.TripPlan?,
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

    private val planning: PlanningFeature = requireNotNull(graph) {
        "No graph yet — call createChargeStopsFeature first"
    }.get()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun planTrip(
        from: org.julakali.chargeahead.shared.domain.LatLon,
        destination: org.julakali.chargeahead.shared.domain.Destination,
        onResult: (TripPlanOutcome) -> Unit,
    ) {
        scope.launch {
            val outcome = when (val result = planning.planTrip(from, destination)) {
                is org.julakali.chargeahead.shared.core.TripPlanResult.Planned ->
                    TripPlanOutcome(result.plan, null)

                is org.julakali.chargeahead.shared.core.TripPlanResult.NoVehicle ->
                    TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_VEHICLE)

                is org.julakali.chargeahead.shared.core.TripPlanResult.NoRoute ->
                    TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_ROUTE)

                is org.julakali.chargeahead.shared.core.TripPlanResult.NoChargerInReach ->
                    TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_CHARGER_IN_REACH)
            }
            onResult(outcome)
        }
    }

    fun chargeNow(
        position: org.julakali.chargeahead.shared.domain.LatLon,
        onResult: (org.julakali.chargeahead.shared.core.ChargeNowResult) -> Unit,
    ) {
        scope.launch { onResult(planning.chargeNow(position)) }
    }

    fun close() {
        scope.cancel()
    }
}
