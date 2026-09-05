import Foundation
import Shared

/// Bridge to the shared framework for CarPlay and the phone UI.
///
/// Unlike in M0, state is no longer just polled on demand: since the list is
/// derived from location and network data, it changes on its own while
/// driving. `ChargeStopsWatcher` (see IosEntryPoints.kt) turns the Kotlin
/// `StateFlow` into a plain callback; SKIE would later turn this into real
/// `AsyncSequence` collection without changing this interface.
final class ChargeStopsViewModel: ObservableObject {

    /// Current state: list *and* location. Always reported on the main thread.
    @Published private(set) var state: ChargeStopsState

    /// For callers without SwiftUI — CarPlay templates aren't redrawn via
    /// `ObservableObject`, they're swapped out manually.
    var onStateChange: ((ChargeStopsState) -> Void)?

    private let feature: ChargeStopsFeature
    private let watcher: ChargeStopsWatcher

    init() {
        let feature = IosEntryPointsKt.createChargeStopsFeature(
            openChargeMapKey: ChargeStopsViewModel.apiKeyFromBundle()
        )
        self.feature = feature
        self.watcher = ChargeStopsWatcher(feature: feature)
        self.state = feature.currentState

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
        feature.refresh()
    }

    /// The key does not belong in the repository (ARCHITECTURE.md section 6).
    /// It's injected into Info.plist via a build setting; when absent, the
    /// shared module returns demo data and sets `state.isDemo`.
    private static func apiKeyFromBundle() -> String? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "OpenChargeMapApiKey") as? String
        else {
            return nil
        }
        // Trimmed: a trailing space in the .xcconfig otherwise gets
        // URL-encoded into the request, and OCM responds with
        // "Invalid API key" without saying why.
        let key = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return key.isEmpty ? nil : key
    }
}

extension ChargeStopsViewModel {

    /// What to show when there's nothing in the list.
    ///
    /// An empty list without explanation looks like an app bug, even when
    /// it's just that the location hasn't been determined yet.
    ///
    /// No `switch`: Kotlin enums appear in the generated header as
    /// Objective-C classes, not as Swift `enum`s. They can be compared for
    /// equality but not used in pattern matching.
    var statusKey: String {
        if state.isDemo {
            return "status_demo"
        }
        let phase = state.phase
        if phase == ChargeStopsStatePhase.waitingForLocation {
            return "status_waiting_for_location"
        }
        if phase == ChargeStopsStatePhase.loading {
            return "status_loading"
        }
        if phase == ChargeStopsStatePhase.ready {
            return "status_no_stops"
        }
        if state.failure == ChargeStopsStateFailureReason.locationUnavailable {
            return "status_location_unavailable"
        }
        return "status_sites_unavailable"
    }

    var statusText: String {
        NSLocalizedString(statusKey, comment: "")
    }
}
