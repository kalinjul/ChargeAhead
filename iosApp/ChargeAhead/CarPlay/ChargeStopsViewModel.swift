import Foundation
import Shared

/// Bridge to the shared corridor list for CarPlay.
///
/// `ChargeStopsWatcher` (see IosEntryPoints.kt) turns the Kotlin `StateFlow`
/// into a plain callback.
final class ChargeStopsViewModel: ObservableObject {

    /// Current state: list *and* location. Always reported on the main thread.
    @Published private(set) var state: ChargeStopsState

    /// For callers without SwiftUI: CarPlay templates are swapped out manually.
    var onStateChange: ((ChargeStopsState) -> Void)?

    let feature: ChargeStopsFeature
    private let watcher: ChargeStopsWatcher

    init() {
        let feature = SharedEntry.newFeature()
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
}

extension ChargeStopsViewModel {

    /// What to show when there's nothing in the list.
    var statusText: String {
        switch state.phase {
        case .waitingForLocation:
            return localized("phone_status_waiting")
        case .loading:
            return localized("phone_status_loading")
        case .ready:
            return localized("phone_status_no_stops")
        case .failed:
            return state.failure == .locationUnavailable
                ? localized("phone_status_location_unavailable")
                : localized("phone_status_sites_unavailable")
        }
    }
}
