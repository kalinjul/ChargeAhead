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
            backendBaseUrl: ChargeStopsViewModel.bundleValue("ChargeAheadBaseUrl"),
            backendToken: ChargeStopsViewModel.bundleValue("ChargeAheadToken"),
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

    /// Injected into Info.plist via a build setting; the shared module stops
    /// the app when the backend is not configured.
    private static func bundleValue(_ key: String) -> String? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: key) as? String else {
            return nil
        }
        // A trailing space in the .xcconfig would end up in the request.
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}

extension ChargeStopsViewModel {

    /// What to show when there's nothing in the list.
    ///
    /// No `switch`: Kotlin enums arrive as Objective-C classes, which can be
    /// compared for equality but not pattern-matched.
    var statusKey: String {
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
