import UIKit
import CarPlay
import Shared

/// Selects the scene configuration for the connecting session's role: phone
/// UI (SwiftUI) or CarPlay template UI. Both are declared in Info.plist.
class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        switch connectingSceneSession.role {
        case .carTemplateApplication:
            let configuration = UISceneConfiguration(
                name: "CarPlay Configuration",
                sessionRole: connectingSceneSession.role
            )
            configuration.delegateClass = CarPlaySceneDelegate.self
            return configuration

        default:
            let configuration = UISceneConfiguration(
                name: "Phone Configuration",
                sessionRole: connectingSceneSession.role
            )
            configuration.delegateClass = PhoneSceneDelegate.self
            return configuration
        }
    }
}

/// The shared entry points both scenes build on.
enum SharedEntry {

    /// One settings store for the whole process, so the feature reads the
    /// same flows the settings UI writes.
    static let settingsStore: SettingsStore = IosEntryPointsKt.createSettingsStore()

    /// A feature the caller owns and closes.
    static func newFeature() -> ChargeStopsFeature {
        IosEntryPointsKt.createChargeStopsFeature(
            backendBaseUrl: bundleValue("ChargeAheadBaseUrl"),
            backendToken: bundleValue("ChargeAheadToken"),
            settingsStore: settingsStore
        )
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
