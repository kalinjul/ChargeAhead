package de.autoapp.shared

import de.autoapp.shared.data.CoreLocationSource
import de.autoapp.shared.db.DatabaseFactory
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.settings.PersistentSettingsStore
import de.autoapp.shared.settings.UserDefaultsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The entry point for Swift. Two things that would otherwise be awkward from Swift:
 *
 * 1. Kotlin's default arguments don't appear in the generated Objective-C
 *    header — [ChargeStopsFeatureFactory.create] would have to be called from
 *    Swift with all parameters, including the clock.
 * 2. A `StateFlow` can't be subscribed to as an `AsyncSequence` or Combine
 *    publisher without SKIE. [ChargeStopsWatcher] turns it into a plain callback.
 *
 * Only compiled on macOS — unverified on this Linux machine.
 */
fun createSettingsStore(): SettingsStore =
    PersistentSettingsStore(UserDefaultsStorage())

/**
 * @param settingsStore the same instance that also backs the settings view —
 *   otherwise the feature would never see changes the driver makes.
 */
fun createChargeStopsFeature(
    openChargeMapKey: String?,
    settingsStore: SettingsStore,
): ChargeStopsFeature =
    ChargeStopsFeatureFactory.create(
        locationSource = CoreLocationSource(),
        openChargeMapKey = openChargeMapKey,
        settingsStore = settingsStore,
        databaseFactory = DatabaseFactory(),
        // No vehicle-data API on iOS (ARCHITECTURE.md 1.2).
        hardwareSoCSource = null,
    )

/**
 * Reports every state change to Swift.
 *
 * Runs on [Dispatchers.Main] so the callback arrives on the main thread
 * without further effort — CarPlay and SwiftUI updates are required to happen there.
 */
class ChargeStopsWatcher(private val feature: ChargeStopsFeature) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start(onChange: (ChargeStopsState) -> Unit) {
        scope.launch { feature.state.collect(onChange) }
    }

    fun stop() {
        scope.cancel()
    }
}

/**
 * Trip planning outcome, flattened for Swift — the sealed
 * [de.autoapp.shared.core.TripPlanResult] would arrive in the Objective-C
 * header as unrelated classes whose exhaustiveness Swift can't check
 * (same reasoning as [ChargeStopsState]).
 */
data class TripPlanOutcome(
    val plan: de.autoapp.shared.core.TripPlan?,
    val failure: TripPlanFailure?,
) {
    enum class TripPlanFailure { NO_VEHICLE, NO_ROUTE, NO_CHARGER_IN_REACH }
}

/**
 * The phone planning flows as plain callbacks on the main thread — the same
 * bridge pattern as [ChargeStopsWatcher], for the same reason: Kotlin
 * `suspend` crosses to Swift as a completion handler with awkward types and
 * without default arguments.
 */
class PlanningBridge(feature: ChargeStopsFeature) {

    private val planning = requireNotNull(feature.planning) {
        "Feature was assembled without planning — use ChargeStopsFeatureFactory.create"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun planTrip(
        from: de.autoapp.shared.domain.LatLon,
        destination: de.autoapp.shared.domain.Destination,
        onResult: (TripPlanOutcome) -> Unit,
    ) {
        scope.launch {
            val outcome = when (val result = planning.planTrip(from, destination)) {
                is de.autoapp.shared.core.TripPlanResult.Planned ->
                    TripPlanOutcome(result.plan, null)

                is de.autoapp.shared.core.TripPlanResult.NoVehicle ->
                    TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_VEHICLE)

                is de.autoapp.shared.core.TripPlanResult.NoRoute ->
                    TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_ROUTE)

                is de.autoapp.shared.core.TripPlanResult.NoChargerInReach ->
                    TripPlanOutcome(null, TripPlanOutcome.TripPlanFailure.NO_CHARGER_IN_REACH)
            }
            onResult(outcome)
        }
    }

    fun chargeNow(
        position: de.autoapp.shared.domain.LatLon,
        onResult: (de.autoapp.shared.core.ChargeNowResult) -> Unit,
    ) {
        scope.launch { onResult(planning.chargeNow(position)) }
    }

    fun close() {
        scope.cancel()
    }
}
