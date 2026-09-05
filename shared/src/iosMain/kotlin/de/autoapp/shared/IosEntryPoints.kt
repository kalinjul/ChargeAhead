package de.autoapp.shared

import de.autoapp.shared.data.CoreLocationSource
import de.autoapp.shared.db.DatabaseDriverFactory
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
        databaseDriverFactory = DatabaseDriverFactory(),
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
