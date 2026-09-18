import Foundation
import Shared

/// Bridge to the shared framework for CarPlay and the phone UI.
///
/// `ChargeStopsWatcher` (see IosEntryPoints.kt) turns the Kotlin `StateFlow`
/// into a plain callback.
final class ChargeStopsViewModel: ObservableObject {

    /// Current state: list *and* location. Always reported on the main thread.
    @Published private(set) var state: ChargeStopsState

    /// For callers without SwiftUI: CarPlay templates are swapped out manually.
    var onStateChange: ((ChargeStopsState) -> Void)?

    /// One settings store for the whole process, so the feature reads the
    /// same flows the settings UI writes.
    static let settingsStore: SettingsStore = IosEntryPointsKt.createSettingsStore()

    let feature: ChargeStopsFeature
    private let watcher: ChargeStopsWatcher

    init() {
        let feature = IosEntryPointsKt.createChargeStopsFeature(
            openChargeMapKey: ChargeStopsViewModel.apiKeyFromBundle(),
            settingsStore: ChargeStopsViewModel.settingsStore
        )
        self.feature = feature
        let watcher = ChargeStopsWatcher(feature: feature)
        self.watcher = watcher
        self.state = watcher.currentState

        watcher.start { [weak self] updated in
            guard let self = self else { return }
            self.state = updated
            self.onStateChange?(updated)
        }
        feature.start()
    }

    deinit {
        watcher.stop()
        feature.close()
    }

    var stops: [ChargeStop] {
        state.stops as? [ChargeStop] ?? []
    }

    func refresh() {
        watcher.refresh()
    }

    /// Injected into Info.plist via a build setting; when absent, the shared
    /// module returns demo data and sets `state.isDemo`.
    private static func apiKeyFromBundle() -> String? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "OpenChargeMapApiKey") as? String
        else {
            return nil
        }
        // A trailing space in the .xcconfig would be URL-encoded into the request.
        let key = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return key.isEmpty ? nil : key
    }
}

extension ChargeStopsViewModel {

    /// What to show when there's nothing in the list.
    ///
    /// No `switch`: Kotlin enums arrive as Objective-C classes, which can be
    /// compared for equality but not pattern-matched.
    var statusKey: String {
        if state.isDemo {
            return "status_demo"
        }
        let phase = state.phase
        if phase == ChargeStopsState.Phase.waitingForLocation {
            return "status_waiting_for_location"
        }
        if phase == ChargeStopsState.Phase.loading {
            return "status_loading"
        }
        if phase == ChargeStopsState.Phase.ready {
            return "status_no_stops"
        }
        if state.failure == ChargeStopsState.FailureReason.locationUnavailable {
            return "status_location_unavailable"
        }
        return "status_sites_unavailable"
    }

    var statusText: String {
        NSLocalizedString(statusKey, comment: "")
    }
}
